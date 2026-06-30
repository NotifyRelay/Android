package org.fcitx.fcitx5.android;

interface IFcitxRemoteService {
    boolean requestPairing(String pairingCode, String packageName, String appName);
    boolean revokePairing(String packageName);
    boolean isAppPaired(String packageName);
}
