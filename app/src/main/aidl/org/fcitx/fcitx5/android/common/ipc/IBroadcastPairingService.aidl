package org.fcitx.fcitx5.android.common.ipc;

interface IBroadcastPairingService {
    boolean requestPairing(String pairingCode, String packageName, String appName);
    boolean revokePairing(String packageName);
    boolean isAppPaired(String packageName);
    String getSharedKey(String packageName);
}
