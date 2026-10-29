package notifyrelay.desktop.platform

import java.awt.GridLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.SwingUtilities

/** 解锁密码输入对话框（Swing，同步等待结果）。 */
internal object DesktopPasswordPrompt {
    /**
     * 询问设备解锁密码。
     *
     * @param deviceName 设备显示名，用于提示文案
     * @return 输入的密码；取消时返回 null
     */
    fun ask(deviceName: String): String? {
        var password: String? = null
        val task =
            Runnable {
                val field = JPasswordField(20)
                val panel =
                    JPanel(GridLayout(2, 1)).apply {
                        add(JLabel("设备「" + deviceName + "」已锁屏，请输入解锁密码："))
                        add(field)
                    }
                val result =
                    JOptionPane.showConfirmDialog(
                        null,
                        panel,
                        "解锁设备",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE,
                    )
                if (result == JOptionPane.OK_OPTION) {
                    password = String(field.password)
                }
            }
        if (SwingUtilities.isEventDispatchThread()) {
            task.run()
        } else {
            SwingUtilities.invokeAndWait(task)
        }
        return password
    }
}
