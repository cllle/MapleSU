package dev.maplesu.manager.runtime.ultra.bridge;

public final class NativeBridge {
    public static native String modulePolicySettings(String rootKey, int requested);
    public static native String publishPolicyBackend(String rootKey, String sha256, byte[] verifiedBytes);
    static {
        String bootLibrary = System.getProperty("maplesu.ultra.boot.library");
        if (bootLibrary != null && android.os.Process.myUid() == 0) {
            System.load(bootLibrary);
        } else {
            System.loadLibrary("maplesu_ultra_bridge");
        }
    }

    private NativeBridge() {}

    public static native String installEnvironment(String rootKey, String insMode, String insExploitMethod);
    public static native String uninstallEnvironment(String rootKey);
    public static native String getEnvironmentState(String rootKey);
    public static native String getInstalledEnvironmentVersion(String rootKey);
    public static native String getSdkVersion();
    public static native String[] captureModuleEnvironment(String rootKey);

    public static native String getSdkBuildIdentity();

    public static native String getSystemStatusJson();

    public static native String testRoot(String rootKey);
    public static native String recoverImageCredentials(byte[] kernel);
    public static native String claimManagerIdentity();
    public static native String queryManagerOwnerIdentity();
    public static native String probeManagerOwnerRoot();
    public static native String runRootCmd(String rootKey, String cmd);
    public static native String readInstalledHelper(String rootKey, String helperPath, long keySlotOffset, int timeoutMillis);

    public static native String addSuAuth(String rootKey, String appPackageName);
    public static native String refreshSuAuthList(String rootKey);
    public static native String removeSuAuth(String rootKey, String modUuid);
    public static native String getSuAuthList(String rootKey);
    public static native String clearSuAuthList(String rootKey);
    public static native String setSuUidProfile(String rootKey, int uid, String packageName, boolean allow);
    public static native String getSuUidProfiles(String rootKey);

    public static native String installModule(String rootKey, String zipFilePath, boolean isDevRunOnceMode);
    public static native String uninstallModule(String rootKey, String modUuid);
    public static native String getModuleList(String rootKey);
    public static native String parseModuleDescriptor(String rootKey, String zipFilePath);

    public static native String getModuleControlCapabilities(String rootKey);
    public static native String getModuleControl(String rootKey, String id32);
    public static native String setModuleEnabled(String rootKey, String id32, boolean enabled);

    public static native String openModuleWebUi(String rootKey, String modUuid);

    public static native String setBootFailProtectEnabled(String rootKey, boolean enable);
    public static native String isBootFailProtectEnabled(String rootKey);

    public static native String setAdbForcedDisabled(String rootKey, boolean enable);
    public static native String isAdbForcedDisabled(String rootKey);

    public static native String testBasics(String rootKey, String item);
    public static native String testDefaultModule(String rootKey, String name);
    public static native String restartZygote64(String rootKey);

    public static native String setLogEnabled(String rootKey, boolean enable);
    public static native String isLogEnabled(String rootKey);
    public static native String readLog(String rootKey);
    public static native String clearLog(String rootKey);

    public static native String oneplusBypassWriteStage1(String rootKey);
    public static native String oneplusBypassIsWorkNormal(String rootKey);

    public static native String parasitePrecheckApp(String rootKey, String targetPidCmdline);
    public static native String parasiteImplantApp(String rootKey, String targetPidCmdline, String soPath);
    public static native String parasiteImplantSuEnv(String rootKey, String targetPidCmdline, String soPath, String suDir);
}
