package notifyrelay.desktop.platform

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import notifyrelay.base.log.Logger

/**
 * 现代 Windows 文件打开对话框（COM `IFileOpenDialog`）。
 *
 * 相对 AWT `FileDialog` 与 Swing `JFileChooser`：
 * - 使用系统当前的文件浏览控件（与资源管理器一致，含搜索框、导航窗格、最近访问）；
 * - 窗口由系统托管，继承宿主进程图标与系统主题，不再是 JDK 旧式外观。
 *
 * COM 调用经 JNA 虚表完成（`Ole32` 提供 CoCreateInstance/CoInitialize，
 * `IFileOpenDialog` 的方法按 IFileDialog 既定顺序取下标）。
 */
internal object ComFileOpenDialog {
    private const val TAG = "ComFileDialog"

    /** FOS_FORCEFILESYSTEM：只允许文件系统路径。 */
    private const val FOS_FORCEFILESYSTEM = 0x00000040

    /** FOS_FILEMUSTEXIST：必须选择已存在的文件。 */
    private const val FOS_FILEMUSTEXIST = 0x00001000

    /** FOS_PICKFOLDERS：选择文件夹而非文件。 */
    private const val FOS_PICKFOLDERS = 0x00000020

    /** SIGDN_FILESYSPATH：以文件系统路径返回显示名。 */
    private const val SIGDN_FILESYSPATH = 0x80058000L

    /** 虚表下标：IModalWindow::Show。 */
    private const val IDX_SHOW = 3

    /** 虚表下标：IFileDialog::SetOptions / GetOptions。 */
    private const val IDX_SET_OPTIONS = 9
    private const val IDX_GET_OPTIONS = 10

    /** 虚表下标：IFileDialog::SetFolder。 */
    private const val IDX_SET_FOLDER = 12

    /** 虚表下标：IFileDialog::SetTitle。 */
    private const val IDX_SET_TITLE = 17

    /** 虚表下标：IFileDialog::GetResult。 */
    private const val IDX_GET_RESULT = 20

    /** 虚表下标：IShellItem::GetDisplayName。 */
    private const val IDX_GET_DISPLAY_NAME = 5

    /** 虚表下标：IUnknown::Release。 */
    private const val IDX_RELEASE = 2

    /**
     * 弹出文件打开对话框。
     *
     * @param title 对话框标题
     * @param pickFolder 为 true 时选择文件夹而非文件
     * @param initialPath 初始定位路径：文件则定位其所在目录，目录则直接定位；为空时由系统决定
     * @return 选中项的完整路径；取消或失败时返回 null
     */
    fun open(
        title: String,
        pickFolder: Boolean = false,
        initialPath: String? = null,
    ): String? {
        if (!isWindows()) return null
        var dialog: Pointer? = null
        var comInitialized = false
        try {
            // COINIT_APARTMENTTHREADED：文件对话框要求 STA
            runCatching {
                Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
                comInitialized = true
            }

            dialog = createFileOpenDialog() ?: return null

            // IFileDialog::SetTitle
            callWideString(dialog, IDX_SET_TITLE, title)

            // IFileDialog::GetOptions(10) / SetOptions(9)
            val options = functionAt(dialog, IDX_GET_OPTIONS).invokeInt(arrayOf(dialog))
            var next = options or FOS_FORCEFILESYSTEM
            next = if (pickFolder) next or FOS_PICKFOLDERS else next or FOS_FILEMUSTEXIST
            functionAt(dialog, IDX_SET_OPTIONS).invokeInt(arrayOf(dialog, next))

            // 初始定位：IFileDialog::SetFolder(12)，参数为目录的 IShellItem
            initialPath?.takeIf { it.isNotBlank() }?.let { path ->
                resolveInitialDirectory(path)?.let { folder ->
                    runCatching { functionAt(dialog, IDX_SET_FOLDER).invokeInt(arrayOf(dialog, folder)) }
                    release(folder)
                }
            }

            // IModalWindow::Show；取消返回非 S_OK
            if (functionAt(dialog, IDX_SHOW).invokeInt(arrayOf(dialog, null)) != 0) {
                return null
            }

            // IFileDialog::GetResult -> IShellItem
            val itemRef = PointerByReference()
            if (functionAt(dialog, IDX_GET_RESULT).invokeInt(arrayOf(dialog, itemRef)) != 0) {
                return null
            }
            val item = itemRef.value ?: return null
            return try {
                readDisplayName(item)
            } finally {
                release(item)
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "文件对话框调用失败", e)
            return null
        } finally {
            dialog?.let { release(it) }
            if (comInitialized) {
                runCatching { Ole32.INSTANCE.CoUninitialize() }
            }
        }
    }

    /** 经 CoCreateInstance 创建 IFileOpenDialog。 */
    private fun createFileOpenDialog(): Pointer? {
        val clsid = GUID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
        val iid = GUID("{D57C7288-D4AD-4768-BE02-9D969532D960}")
        val result = PointerByReference()
        val hr =
            try {
                // CLSCTX_INPROC_SERVER = 1（jna-platform 的 Ole32 未导出该常量）
                Ole32.INSTANCE.CoCreateInstance(clsid, null, 1, iid, result).toInt()
            } catch (e: Throwable) {
                Logger.w(TAG, "CoCreateInstance 调用失败", e)
                return null
            }
        if (hr != 0 || result.value == null) {
            Logger.w(TAG, "创建 IFileOpenDialog 失败: hr=" + hr)
            return null
        }
        return result.value
    }

    /** 读取 IShellItem 的文件系统路径。 */
    private fun readDisplayName(item: Pointer): String? {
        val pathRef = PointerByReference()
        val hr = functionAt(item, IDX_GET_DISPLAY_NAME).invokeInt(arrayOf(item, SIGDN_FILESYSPATH, pathRef))
        if (hr != 0) return null
        val ptr = pathRef.value ?: return null
        val path =
            try {
                ptr.getWideString(0)
            } finally {
                runCatching { Ole32.INSTANCE.CoTaskMemFree(ptr) }
            }
        return path
    }

    /**
     * 把初始路径解析为目录的 IShellItem。
     *
     * 传入的是文件时取其父目录；路径不存在时逐级上溯到第一个存在的目录。
     */
    private fun resolveInitialDirectory(path: String): Pointer? {
        var file = java.io.File(path)
        if (file.isFile) file = file.parentFile ?: return null
        while (!file.exists() && file.parentFile != null) {
            file = file.parentFile!!
        }
        if (!file.exists()) return null
        return shellItemFromPath(file.absolutePath)
    }

    /** SHCreateItemFromParsingName：由路径创建 IShellItem。 */
    private fun shellItemFromPath(path: String): Pointer? {
        val library = shell32Library ?: return null
        val result = PointerByReference()
        val hr =
            runCatching {
                library
                    .getFunction("SHCreateItemFromParsingName")
                    .invokeInt(arrayOf(WString(path), null, shellItemIid(), result))
            }.getOrNull() ?: return null
        if (hr != 0 || result.value == null) return null
        return result.value
    }

    /** IID_IShellItem。 */
    private fun shellItemIid(): Pointer =
        shellItemIidRef ?: run {
            val guid = GUID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")
            // GUID 的内存由 JNA 结构体持有，需保持引用避免被回收
            shellItemIidHolder = guid
            guid.pointer.also { shellItemIidRef = it }
        }

    private var shellItemIidRef: Pointer? = null
    private var shellItemIidHolder: GUID? = null

    private val shell32Library: com.sun.jna.NativeLibrary? by lazy {
        runCatching {
            com.sun.jna.NativeLibrary
                .getInstance("shell32")
        }.getOrNull()
    }

    /** 取 COM 虚表指定下标处的函数指针。 */
    private fun functionAt(
        instance: Pointer,
        index: Int,
    ): Function {
        val vtable = instance.getPointer(0)
        val fn = vtable.getPointer(index.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(fn, Function.ALT_CONVENTION)
    }

    /** 调用接收单个宽字符串参数的方法。 */
    private fun callWideString(
        instance: Pointer,
        index: Int,
        value: String,
    ) {
        functionAt(instance, index).invokeInt(arrayOf(instance, WString(value)))
    }

    /** IUnknown::Release。 */
    private fun release(instance: Pointer) {
        runCatching { functionAt(instance, IDX_RELEASE).invokeInt(arrayOf(instance)) }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
