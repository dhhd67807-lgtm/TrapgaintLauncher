package com.movtery.zalithlauncher.launch

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kdt.mcgui.ProgressLayout
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.event.single.AccountUpdateEvent
import com.movtery.zalithlauncher.feature.accounts.AccountType
import com.movtery.zalithlauncher.feature.accounts.AccountUtils
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.download.enums.Classify
import com.movtery.zalithlauncher.feature.download.enums.Platform
import com.movtery.zalithlauncher.feature.download.enums.VersionType as DownloadVersionType
import com.movtery.zalithlauncher.feature.download.item.VersionItem
import com.movtery.zalithlauncher.feature.download.platform.modrinth.ModrinthCommonUtils
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.mod.modloader.DragonClientDownloadTask
import com.movtery.zalithlauncher.feature.mod.modloader.DragonClientManifestResolver
import com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeApiModDownloadTask
import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.renderer.Renderers
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.AllStaticSettings
import com.movtery.zalithlauncher.support.touch_controller.ControllerProxy
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.LifecycleAwareTipDialog
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.http.NetworkUtils
import com.movtery.zalithlauncher.utils.stringutils.StringUtils
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.JMinecraftVersionList
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.authenticator.microsoft.PresentedException
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import net.kdt.pojavlaunch.plugins.FFmpegPlugin
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.services.GameService
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader
import net.kdt.pojavlaunch.tasks.MinecraftDownloader
import net.kdt.pojavlaunch.utils.JREUtils
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.json.JSONObject
import org.greenrobot.eventbus.EventBus

class LaunchGame {
    companion object {
        private val MC_VERSION_REGEX = Regex("""(?<!\d)(\d+)\.(\d+)(?:\.(\d+))?(?!\d)""")
        private val OPTION_LIST_REGEX = Regex("\"((?:\\\\.|[^\"])*)\"")
        private const val CLOUD_COMPAT_PACK_ID = "cloud_gles_compat"
        private const val DEFAULT_RESOURCE_PACK_FORMAT = 75
        private const val FABRIC_API_PROJECT_ID = "P7dR8mSH"
        private val DRAGON_SUPPORTED_VERSIONS = setOf(
            "1.21.1",
            "1.21.3",
            "1.21.4",
            "1.21.6",
            "1.21.7",
            "1.21.8",
            "1.21.10",
            "1.21.11"
        )

        /**
         * 改为启动游戏前进行的操作
         * - 进行登录，同时也能及时的刷新账号的信息（这明显更合理不是吗，PojavLauncher？）
         * - 复制 options.txt 文件到游戏目录
         * @param version 选择的版本
         */
        @JvmStatic
        fun preLaunch(context: Context, version: Version) {
            val networkAvailable = NetworkUtils.isNetworkAvailable(context)

            fun launch(setOfflineAccount: Boolean = false) {
                version.offlineAccountLogin = setOfflineAccount

                val versionName = version.getVersionName()
                val mcVersion = AsyncMinecraftDownloader.getListedVersion(versionName)
                val listener = ContextAwareDoneListener(context, version)
                //若网络未连接，跳过下载任务直接启动
                if (!networkAvailable) {
                    listener.onDownloadDone()
                } else {
                    MinecraftDownloader().start(mcVersion, versionName, listener)
                }
            }

            fun setGameProgress(pull: Boolean) {
                if (pull) {
                    ProgressKeeper.submitProgress(ProgressLayout.CHECKING_MODS, 0, R.string.mod_check_progress_message, 0, 0, 0)
                    ProgressKeeper.submitProgress(ProgressLayout.DOWNLOAD_MINECRAFT, 0, R.string.newdl_downloading_game_files, 0, 0, 0)
                } else {
                    ProgressLayout.clearProgress(ProgressLayout.DOWNLOAD_MINECRAFT)
                    ProgressLayout.clearProgress(ProgressLayout.CHECKING_MODS)
                }
            }

            if (!networkAvailable) {
                // 网络未链接，无法登录，但是依旧允许玩家启动游戏 (临时创建一个同名的离线账号启动游戏)
                Toast.makeText(context, context.getString(R.string.account_login_no_network), Toast.LENGTH_SHORT).show()
                launch(true)
                return
            }

            if (AccountUtils.isNoLoginRequired(AccountsManager.currentAccount)) {
                launch()
                return
            }

            AccountsManager.performLogin(
                context, AccountsManager.currentAccount!!,
                { _ ->
                    EventBus.getDefault().post(AccountUpdateEvent())
                    TaskExecutors.runInUIThread {
                        Toast.makeText(context, context.getString(R.string.account_login_done), Toast.LENGTH_SHORT).show()
                    }
                    //登录完成，正式启动游戏！
                    launch()
                },
                { exception ->
                    val errorMessage = if (exception is PresentedException) exception.toString(context)
                    else exception.message

                    TaskExecutors.runInUIThread {
                        TipDialog.Builder(context)
                            .setTitle(R.string.generic_error)
                            .setMessage("${context.getString(R.string.account_login_skip)}\r\n$errorMessage")
                            .setWarning()
                            .setConfirmClickListener { launch(true) }
                            .setCenterMessage(false)
                            .showDialog()
                    }

                    setGameProgress(false)
                }
            )
            setGameProgress(true)
        }

        @Throws(Throwable::class)
        @JvmStatic
        fun runGame(activity: AppCompatActivity, minecraftVersion: Version, version: JMinecraftVersionList.Version) {
            if (!Renderers.isCurrentRendererValid()) {
                Renderers.setCurrentRenderer(activity, AllSettings.renderer.getValue())
            }

            val resolvedMcVersion = resolveMinecraftVersion(minecraftVersion, version)

            // Check renderer compatibility for MC 1.21.5+ and auto-switch if needed
            val currentRenderer = Renderers.getCurrentRenderer()
            
            if (!currentRenderer.supportsVersion(resolvedMcVersion)) {
                Logging.w("LaunchGame", "Current renderer '${currentRenderer.getRendererName()}' does not support MC $resolvedMcVersion")
                
                // Try to find a compatible renderer (prefer NGGL4ES for 1.21.5+)
                val compatibleRenderers = Renderers.getCompatibleRenderers(activity).second
                val compatibleRenderer = compatibleRenderers.firstOrNull { it.supportsVersion(resolvedMcVersion) }
                
                if (compatibleRenderer != null) {
                    Logging.i("LaunchGame", "Auto-switching to compatible renderer: ${compatibleRenderer.getRendererName()}")
                    Renderers.setCurrentRenderer(activity, compatibleRenderer.getUniqueIdentifier())
                    AllSettings.renderer.put(compatibleRenderer.getUniqueIdentifier()).save()
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Switched to ${compatibleRenderer.getRendererName()} for MC $resolvedMcVersion",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Logging.e("LaunchGame", "No compatible renderer found for MC $resolvedMcVersion")
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Warning: Current renderer may not support MC $resolvedMcVersion",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            var account = AccountsManager.currentAccount!!
            if (minecraftVersion.offlineAccountLogin) {
                account = MinecraftAccount().apply {
                    this.username = account.username
                    this.accountType = AccountType.LOCAL.type
                }
            }

            val customArgs = minecraftVersion.getJavaArgs().takeIf { it.isNotBlank() } ?: ""

            // Determine required Java version
            val targetJavaVersion = when {
                // Check version.javaVersion first (from version.json)
                version.javaVersion?.majorVersion != null && version.javaVersion.majorVersion > 0 -> 
                    version.javaVersion.majorVersion
                // Fallback: Parse version string
                else -> {
                    val parsedVersion = parseMinecraftVersion(resolvedMcVersion)
                    if (parsedVersion != null) {
                        val (major, minor, patch) = parsedVersion
                        when {
                            major > 1 -> 21 // Future versions (2.x+)
                            major == 1 && minor >= 21 -> 21 // 1.21+ requires Java 21
                            major == 1 && minor == 20 && patch >= 5 -> 21 // 1.20.5+ requires Java 21
                            major == 1 && minor >= 17 -> 17 // 1.17 - 1.20.4 needs Java 17
                            else -> 8
                        }
                    } else 8
                }
            }
            
            val javaRuntime = getRuntime(activity, minecraftVersion, targetJavaVersion)
            
            Logger.appendToLog("Java Version Detection: MC ${version.id} requires Java $targetJavaVersion")
            Logger.appendToLog("Selected Java Runtime: $javaRuntime")
            
            // Verify the runtime is valid before proceeding
            val runtimeCheck = MultiRTUtils.read(javaRuntime)
            if (runtimeCheck.javaVersion == 0 || runtimeCheck.versionString == null) {
                val errorMsg = "FATAL: Selected runtime '$javaRuntime' is invalid or not installed properly!"
                Logger.appendToLog(errorMsg)
                activity.runOnUiThread {
                    Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
                }
                throw RuntimeException(errorMsg)
            }
            Logger.appendToLog("Runtime verification passed: Java ${runtimeCheck.javaVersion} (${runtimeCheck.versionString})")

            printLauncherInfo(
                minecraftVersion,
                customArgs.takeIf { it.isNotBlank() } ?: "NONE",
                javaRuntime,
                account
            )

            minecraftVersion.modCheckResult?.let { modCheckResult ->
                if (modCheckResult.hasTouchController) {
                    Logger.appendToLog("Mod Perception: TouchController Mod found, attempting to automatically enable control proxy!")
                    ControllerProxy.startProxy(activity)
                    AllStaticSettings.useControllerProxy = true
                }

                if (modCheckResult.hasSodiumOrEmbeddium) {
                    Logger.appendToLog("Mod Perception: Sodium or Embeddium Mod found, attempting to load the disable warning tool later!")
                }
            }

            JREUtils.redirectAndPrintJRELog()

            launch(activity, account, minecraftVersion, javaRuntime, customArgs)

            //Note that we actually stall in the above function, even if the game crashes. But let's be safe.
            GameService.setActive(false)
        }

        private fun resolveMinecraftVersion(minecraftVersion: Version, version: JMinecraftVersionList.Version): String {
            val candidates = listOfNotNull(
                minecraftVersion.getVersionInfo()?.minecraftVersion,
                version.id,
                minecraftVersion.getVersionName()
            )
            for (candidate in candidates) {
                val parsed = parseMinecraftVersion(candidate)
                if (parsed != null) {
                    val (major, minor, patch) = parsed
                    return "$major.$minor.$patch"
                }
            }
            return version.id ?: minecraftVersion.getVersionName()
        }

        private fun parseMinecraftVersion(versionText: String): Triple<Int, Int, Int>? {
            val matches = MC_VERSION_REGEX.findAll(versionText)
                .mapNotNull { match ->
                    val major = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val minor = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                    Triple(major, minor, patch)
                }
                .toList()

            if (matches.isEmpty()) return null
            return matches.firstOrNull { it.first == 1 } ?: matches.first()
        }

        private fun getRuntime(activity: Activity, version: Version, targetJavaVersion: Int): String {
            val versionRuntime = version.getJavaDir()
                .takeIf { it.isNotEmpty() && it.startsWith(Tools.LAUNCHERPROFILES_RTPREFIX) }
                ?.removePrefix(Tools.LAUNCHERPROFILES_RTPREFIX)
                ?: ""

            if (versionRuntime.isNotEmpty()) return versionRuntime

            //如果版本未选择Java环境，则自动选择合适的环境
            var runtime = AllSettings.defaultRuntime.getValue()
            val pickedRuntime = MultiRTUtils.read(runtime)
            if (pickedRuntime.javaVersion == 0 || pickedRuntime.javaVersion < targetJavaVersion) {
                runtime = MultiRTUtils.getNearestJreName(targetJavaVersion) ?: run {
                    // No suitable JRE found, auto-install the required one
                    val jreToInstall = when {
                        targetJavaVersion >= 21 -> com.movtery.zalithlauncher.feature.unpack.Jre.JRE_21
                        targetJavaVersion >= 17 -> com.movtery.zalithlauncher.feature.unpack.Jre.JRE_17
                        else -> com.movtery.zalithlauncher.feature.unpack.Jre.JRE_8
                    }
                    
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity, 
                            activity.getString(R.string.game_autopick_runtime_failed) + " - Installing ${jreToInstall.jreName}...", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    // Install JRE synchronously
                    val unpackTask = com.movtery.zalithlauncher.feature.unpack.UnpackJreTask(activity, jreToInstall)
                    if (!unpackTask.isCheckFailed() && unpackTask.isNeedUnpack()) {
                        try {
                            Logger.appendToLog("Installing ${jreToInstall.jreName} for Java $targetJavaVersion requirement...")
                            unpackTask.run()
                            Logger.appendToLog("JRE installation completed")
                            
                            // Verify installation succeeded
                            val installedRuntime = MultiRTUtils.read(jreToInstall.jreName)
                            if (installedRuntime.javaVersion == 0 || installedRuntime.versionString == null) {
                                Logger.appendToLog("ERROR: Failed to install ${jreToInstall.jreName}, falling back to default runtime")
                                activity.runOnUiThread {
                                    Toast.makeText(
                                        activity,
                                        "Failed to install Java ${targetJavaVersion}. Using default runtime instead.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                // Fall back to any available runtime
                                return MultiRTUtils.getNearestJreName(8) ?: AllSettings.defaultRuntime.getValue()
                            }
                            Logger.appendToLog("JRE verification passed: ${installedRuntime.javaVersion} (${installedRuntime.versionString})")
                        } catch (e: Exception) {
                            Logger.appendToLog("ERROR: Exception during JRE installation: ${e.message}")
                            Logging.e("LaunchGame", "JRE installation failed", e)
                            activity.runOnUiThread {
                                Toast.makeText(
                                    activity,
                                    "Failed to install Java ${targetJavaVersion}: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            // Fall back to any available runtime
                            return MultiRTUtils.getNearestJreName(8) ?: AllSettings.defaultRuntime.getValue()
                        }
                    } else if (unpackTask.isCheckFailed()) {
                        Logger.appendToLog("ERROR: JRE check failed for ${jreToInstall.jreName}")
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "JRE check failed. Using default runtime.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return MultiRTUtils.getNearestJreName(8) ?: AllSettings.defaultRuntime.getValue()
                    }
                    
                    return jreToInstall.jreName
                }
            }
            return runtime
        }

        private fun printLauncherInfo(
            minecraftVersion: Version,
            javaArguments: String,
            javaRuntime: String,
            account: MinecraftAccount
        ) {
            var mcInfo = minecraftVersion.getVersionName()
            minecraftVersion.getVersionInfo()?.let { info ->
                mcInfo = info.getInfoString()
            }

            Logger.appendToLog("--------- Start launching the game")
            Logger.appendToLog("Info: Launcher version: ${ZHTools.getVersionName()} (${ZHTools.getVersionCode()})")
            Logger.appendToLog("Info: Architecture: ${Architecture.archAsString(Tools.DEVICE_ARCHITECTURE)}")
            Logger.appendToLog("Info: Device model: ${StringUtils.insertSpace(Build.MANUFACTURER, Build.MODEL)}")
            Logger.appendToLog("Info: API version: ${Build.VERSION.SDK_INT}")
            Logger.appendToLog("Info: Renderer: ${Renderers.getCurrentRenderer().getRendererName()}")
            Logger.appendToLog("Info: Selected Minecraft version: ${minecraftVersion.getVersionName()}")
            Logger.appendToLog("Info: Minecraft Info: $mcInfo")
            Logger.appendToLog("Info: Game Path: ${minecraftVersion.getGameDir().absolutePath} (Isolation: ${minecraftVersion.isIsolation()})")
            Logger.appendToLog("Info: Custom Java arguments: $javaArguments")
            Logger.appendToLog("Info: Java Runtime: $javaRuntime")
            Logger.appendToLog("Info: Account: ${account.username} (${account.accountType})")
            Logger.appendToLog("---------\r\n")
        }

        @Throws(Throwable::class)
        @JvmStatic
        private fun launch(
            activity: AppCompatActivity,
            account: MinecraftAccount,
            minecraftVersion: Version,
            javaRuntime: String,
            customArgs: String
        ) {
            checkMemory(activity)

            val runtime = MultiRTUtils.forceReread(javaRuntime)

            val versionInfo = Tools.getVersionInfo(minecraftVersion)
            val gameDirPath = minecraftVersion.getGameDir()

            //预处理
            // Create cloud compatibility pack for MC 1.21.5+ BEFORE any other preprocessing
            createCloudCompatibilityPack(minecraftVersion, gameDirPath)
            
            Tools.disableSplash(gameDirPath)
            
            // Copy user skin to game directory for offline accounts
            copySkinToGameDir(account, gameDirPath)

            // Keep Dragon runtime dependencies healthy: re-download missing Dragon/Fabric API mods on launch.
            ensureDragonClientMods(activity, minecraftVersion, gameDirPath)
            
            val launchClassPath = Tools.generateLaunchClassPath(versionInfo, minecraftVersion)

            val launchArgs = LaunchArgs(
                account,
                gameDirPath,
                minecraftVersion,
                versionInfo,
                minecraftVersion.getVersionName(),
                runtime,
                launchClassPath
            ).getAllArgs()

            FFmpegPlugin.discover(activity)

            JREUtils.launchWithUtils(activity, runtime, minecraftVersion, launchArgs, customArgs)
        }

        private fun checkMemory(activity: AppCompatActivity) {
            var freeDeviceMemory = Tools.getFreeDeviceMemory(activity)
            val freeAddressSpace =
                if (Architecture.is32BitsDevice())
                    Tools.getMaxContinuousAddressSpaceSize()
                else -1
            Logging.i("MemStat",
                "Free RAM: $freeDeviceMemory Addressable: $freeAddressSpace")

            val stringId: Int = if (freeDeviceMemory > freeAddressSpace && freeAddressSpace != -1) {
                freeDeviceMemory = freeAddressSpace
                R.string.address_memory_warning_msg
            } else R.string.memory_warning_msg

            if (AllSettings.ramAllocation.value.getValue() > freeDeviceMemory) {
                val builder = TipDialog.Builder(activity)
                    .setTitle(R.string.generic_warning)
                    .setMessage(activity.getString(stringId, freeDeviceMemory, AllSettings.ramAllocation.value.getValue()))
                    .setWarning()
                    .setCenterMessage(false)
                    .setShowCancel(false)
                if (LifecycleAwareTipDialog.haltOnDialog(activity.lifecycle, builder)) return
                // If the dialog's lifecycle has ended, return without
                // actually launching the game, thus giving us the opportunity
                // to start after the activity is shown again
            }
        }
        
        private fun copySkinToGameDir(account: MinecraftAccount, gameDirPath: File) {
            try {
                val skinFile = File(com.movtery.zalithlauncher.utils.path.PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")
                if (!skinFile.exists()) {
                    Logging.i("SkinLoader", "No custom skin found for account: ${account.username}")
                    return
                }
                
                // Copy skin to game directory as char.png (Minecraft's offline skin file)
                val targetSkinFile = File(gameDirPath, "char.png")
                org.apache.commons.io.FileUtils.copyFile(skinFile, targetSkinFile)
                Logging.i("SkinLoader", "Copied skin to game directory: ${targetSkinFile.absolutePath}")
            } catch (e: Exception) {
                Logging.e("SkinLoader", "Failed to copy skin to game directory", e)
            }
        }

        private fun ensureDragonClientMods(activity: AppCompatActivity, version: Version, gameDirPath: File) {
            val versionName = version.getVersionName().lowercase(Locale.ROOT)
            if (!versionName.contains("dragon")) return

            val mcVersion = resolveDragonMinecraftVersion(version) ?: return
            if (!DRAGON_SUPPORTED_VERSIONS.contains(mcVersion)) {
                Logger.appendToLog("Dragon Restore: $mcVersion is not in supported list, skip auto-restore.")
                return
            }

            val modsDir = File(gameDirPath, "mods")
            if (!modsDir.exists()) modsDir.mkdirs()

            val hasDragonClient = modsDir.listFiles()?.any {
                it.isFile && it.name.startsWith("dragon-client-$mcVersion-") && it.name.endsWith(".jar")
            } == true
            val hasFabricApi = modsDir.listFiles()?.any {
                it.isFile && it.name.startsWith("fabric-api") && it.name.endsWith(".jar")
            } == true

            if (hasDragonClient && hasFabricApi) return
            if (!NetworkUtils.isNetworkAvailable(activity)) {
                Logger.appendToLog("Dragon Restore: Network unavailable, cannot restore missing mods.")
                return
            }

            if (!hasDragonClient) {
                runCatching {
                    val asset = DragonClientManifestResolver.resolveLatestAsset(mcVersion)
                        ?: throw IllegalStateException("No Dragon Client asset for $mcVersion")
                    val cachedFile = DragonClientDownloadTask(asset).run(version.getVersionName())
                    val targetFile = File(modsDir, asset.fileName)
                    org.apache.commons.io.FileUtils.copyFile(cachedFile, targetFile)
                    Logger.appendToLog("Dragon Restore: Re-downloaded ${asset.fileName}")
                }.onFailure { e ->
                    Logger.appendToLog("Dragon Restore ERROR (client): ${e.message}")
                    Logging.e("Dragon Restore", "Failed to restore Dragon Client jar", e)
                }
            }

            if (!hasFabricApi) {
                runCatching {
                    val fabricApiVersion = findLatestFabricApiForVersion(mcVersion)
                        ?: throw IllegalStateException("No Fabric API for $mcVersion")
                    val cachedFile = FabricLikeApiModDownloadTask("fabric-api", fabricApiVersion).run(version.getVersionName())
                    val targetFile = File(modsDir, fabricApiVersion.fileName)
                    org.apache.commons.io.FileUtils.copyFile(cachedFile, targetFile)
                    Logger.appendToLog("Dragon Restore: Re-downloaded ${fabricApiVersion.fileName}")
                }.onFailure { e ->
                    Logger.appendToLog("Dragon Restore ERROR (fabric-api): ${e.message}")
                    Logging.e("Dragon Restore", "Failed to restore Fabric API jar", e)
                }
            }
        }

        private fun resolveDragonMinecraftVersion(version: Version): String? {
            version.getVersionInfo()?.minecraftVersion?.let { infoVersion ->
                if (DRAGON_SUPPORTED_VERSIONS.contains(infoVersion)) return infoVersion
            }

            val parsedFromName = parseMinecraftVersion(version.getVersionName()) ?: return null
            val candidate = "${parsedFromName.first}.${parsedFromName.second}.${parsedFromName.third}"
            return if (DRAGON_SUPPORTED_VERSIONS.contains(candidate)) candidate else null
        }

        private fun findLatestFabricApiForVersion(mcVersion: String): VersionItem? {
            val helper = Platform.MODRINTH.helper
            val infoItem = ModrinthCommonUtils.getInfo(helper.api, Classify.MOD, FABRIC_API_PROJECT_ID) ?: return null

            var versions = helper.getVersions(infoItem, false)
            if (versions.isNullOrEmpty()) {
                versions = helper.getVersions(infoItem, true)
            }
            if (versions.isNullOrEmpty()) return null

            return versions
                .asSequence()
                .filter { it.mcVersions.contains(mcVersion) && it.fileName.endsWith(".jar", true) }
                .sortedWith(
                    compareBy<VersionItem> { it.versionType != DownloadVersionType.RELEASE }
                        .thenByDescending { it.uploadDate.time }
                )
                .firstOrNull()
        }
        
        /**
         * Create a resource pack with OpenGL ES compatible cloud shaders for MC 1.21.5+
         * MC 1.21.5+ cloud shaders use GL_EXT_texture_buffer which is not available in OpenGL ES 3.0
         * This creates a resource pack that provides simple compatible shaders
         */
        private fun createCloudCompatibilityPack(version: Version, gameDirPath: File) {
            try {
                val versionName = version.getVersionName()
                val parsedVersion = parseMinecraftVersion(version.getVersionInfo()?.minecraftVersion ?: versionName)

                if (parsedVersion != null) {
                    val (major, minor, patch) = parsedVersion

                    if (major == 1 && (minor > 21 || (minor == 21 && patch >= 5))) {
                        Logger.appendToLog("Cloud Pack: Creating compatibility pack for MC $versionName")
                        
                        if (!gameDirPath.exists()) {
                            gameDirPath.mkdirs()
                        }
                        
                        val resourcePacksDir = File(gameDirPath, "resourcepacks")
                        if (!resourcePacksDir.exists()) {
                            resourcePacksDir.mkdirs()
                        }
                        
                        val packDir = File(resourcePacksDir, CLOUD_COMPAT_PACK_ID)
                        if (packDir.exists()) {
                            packDir.deleteRecursively()
                        }
                        packDir.mkdirs()
                        
                        // Create pack.mcmeta
                        val packMcmeta = File(packDir, "pack.mcmeta")
                        val packFormat = resolveResourcePackFormat(version, gameDirPath)
                        packMcmeta.writeText(
                            """{"pack":{"pack_format":$packFormat,"description":"OpenGL ES Cloud Compatibility"}}"""
                        )
                        
                        // Create core shader directory
                        val coreShaderDir = File(packDir, "assets/minecraft/shaders/core")
                        coreShaderDir.mkdirs()
                        
                        // Create rendertype_clouds.vsh - simple passthrough vertex shader
                        val cloudsVsh = File(coreShaderDir, "rendertype_clouds.vsh")
                        cloudsVsh.writeText("""#version 150
in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}""")
                        
                        // Create rendertype_clouds.fsh - discard all fragments
                        val cloudsFsh = File(coreShaderDir, "rendertype_clouds.fsh")
                        cloudsFsh.writeText("""#version 150
out vec4 fragColor;
void main() {
    discard;
}""")
                        
                        // Create rendertype_clouds.json
                        val cloudsJson = File(coreShaderDir, "rendertype_clouds.json")
                        cloudsJson.writeText("""{"vertex":"rendertype_clouds","fragment":"rendertype_clouds","attributes":["Position"],"uniforms":[{"name":"ModelViewMat","type":"matrix4x4","count":16,"values":[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]},{"name":"ProjMat","type":"matrix4x4","count":16,"values":[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]}]}""")
                        
                        // Enable the resource pack in options.txt.
                        // Keep it in incompatible list too, so users don't get auto-removed if Mojang bumps formats.
                        val optionsFile = File(gameDirPath, "options.txt")
                        val lines = if (optionsFile.exists()) optionsFile.readLines().toMutableList() else mutableListOf()
                        val compatPackRef = "file/$CLOUD_COMPAT_PACK_ID"
                        upsertOptionList(lines, "resourcePacks", compatPackRef)
                        upsertOptionList(lines, "incompatibleResourcePacks", compatPackRef)
                        upsertOptionValue(lines, "renderClouds", "\"false\"")
                        optionsFile.writeText(lines.joinToString("\n", postfix = "\n"))
                        
                        Logger.appendToLog("Cloud Pack: Created at ${packDir.absolutePath}")
                        Logging.i("LaunchGame", "Created OpenGL ES cloud compatibility pack for MC $versionName (pack_format=$packFormat)")
                    }
                }
            } catch (e: Exception) {
                Logger.appendToLog("Cloud Pack ERROR: ${e.message}")
                Logging.e("LaunchGame", "Failed to create cloud compatibility pack", e)
            }
        }

        private fun resolveResourcePackFormat(version: Version, gameDirPath: File): Int {
            val versionTokenCandidates = linkedSetOf<String>()
            version.getVersionInfo()?.minecraftVersion?.let { versionTokenCandidates.addAll(extractVersionTokens(it)) }
            version.getVersionName().let { versionTokenCandidates.addAll(extractVersionTokens(it)) }

            for (versionToken in versionTokenCandidates) {
                val localJar = File(gameDirPath, "$versionToken.jar")
                if (localJar.exists()) {
                    extractResourcePackFormatFromJar(localJar)?.let { return it }
                }

                val sharedJar = File(gameDirPath, "versions/$versionToken/$versionToken.jar")
                if (sharedJar.exists()) {
                    extractResourcePackFormatFromJar(sharedJar)?.let { return it }
                }
            }

            return DEFAULT_RESOURCE_PACK_FORMAT
        }

        private fun extractVersionTokens(rawText: String): List<String> {
            return MC_VERSION_REGEX.findAll(rawText).map { it.value }.toList()
        }

        private fun extractResourcePackFormatFromJar(jarFile: File): Int? {
            return try {
                ZipFile(jarFile).use { zipFile ->
                    val versionEntry = zipFile.getEntry("version.json") ?: return null
                    val versionJson = zipFile.getInputStream(versionEntry).bufferedReader().use { it.readText() }
                    val packVersion = JSONObject(versionJson).optJSONObject("pack_version") ?: return null
                    val resourceMajor = packVersion.optInt("resource_major", -1)
                    if (resourceMajor > 0) resourceMajor else null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun upsertOptionValue(lines: MutableList<String>, key: String, value: String) {
            val line = "$key:$value"
            val existingIndex = lines.indexOfFirst { it.startsWith("$key:") }
            if (existingIndex >= 0) {
                lines[existingIndex] = line
            } else {
                lines.add(line)
            }
        }

        private fun upsertOptionList(lines: MutableList<String>, key: String, item: String) {
            val existingIndex = lines.indexOfFirst { it.startsWith("$key:") }
            if (existingIndex >= 0) {
                val values = parseOptionList(lines[existingIndex].substringAfter(':')).toMutableList()
                if (!values.contains(item)) values.add(item)
                lines[existingIndex] = "$key:[${values.joinToString(",") { "\"$it\"" }}]"
            } else {
                lines.add("$key:[\"$item\"]")
            }
        }

        private fun parseOptionList(rawValue: String): List<String> {
            return OPTION_LIST_REGEX.findAll(rawValue).map { match ->
                match.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }.toList()
        }
        
        /**
         * Disable clouds for MC 1.21.5+ to fix OpenGL ES shader compilation issues
         * Cloud shaders in MC 1.21.5+ use GL_EXT_texture_buffer which is not available in OpenGL ES 3.0
         */
        private fun disableCloudsForNewVersions(version: Version, gameDirPath: File) {
            try {
                val versionName = version.getVersionName()
                Logger.appendToLog("Cloud Fix: Checking version $versionName")
                
                // Extract base version number (e.g., "1.21.11" from "1.21.11 Fabric")
                val baseVersion = versionName.split(" ")[0]
                val parts = baseVersion.split(".")
                
                Logger.appendToLog("Cloud Fix: Base version = $baseVersion, parts = ${parts.joinToString(",")}")
                
                if (parts.size >= 2) {
                    val major = parts[0].toIntOrNull() ?: 0
                    val minor = parts[1].substringBefore("-").toIntOrNull() ?: 0
                    
                    Logger.appendToLog("Cloud Fix: major=$major, minor=$minor")
                    
                    // Only apply fix for MC 1.21.5+
                    if (major == 1 && minor >= 21) {
                        val patch = if (parts.size >= 3) parts[2].substringBefore("-").toIntOrNull() ?: 0 else 0
                        Logger.appendToLog("Cloud Fix: patch=$patch")
                        
                        if (patch >= 5 || (minor > 21)) {
                            Logger.appendToLog("Cloud Fix: Version qualifies for cloud fix!")
                            
                            // Ensure game directory exists
                            if (!gameDirPath.exists()) {
                                gameDirPath.mkdirs()
                                Logger.appendToLog("Cloud Fix: Created game directory: ${gameDirPath.absolutePath}")
                            }
                            
                            // Disable clouds in options.txt
                            val optionsFile = File(gameDirPath, "options.txt")
                            Logger.appendToLog("Cloud Fix: Options file path: ${optionsFile.absolutePath}, exists=${optionsFile.exists()}")
                            
                            if (optionsFile.exists()) {
                                val lines = optionsFile.readLines().toMutableList()
                                var cloudsFound = false
                                
                                for (i in lines.indices) {
                                    if (lines[i].startsWith("renderClouds:")) {
                                        val oldValue = lines[i]
                                        lines[i] = "renderClouds:\"false\""
                                        cloudsFound = true
                                        Logger.appendToLog("Cloud Fix: Changed '$oldValue' to '${lines[i]}'")
                                        break
                                    }
                                }
                                
                                if (!cloudsFound) {
                                    lines.add("renderClouds:\"false\"")
                                    Logger.appendToLog("Cloud Fix: Added renderClouds:\"false\" to options.txt")
                                }
                                
                                optionsFile.writeText(lines.joinToString("\n"))
                                Logger.appendToLog("Cloud Fix: Wrote options.txt")
                            } else {
                                optionsFile.writeText("renderClouds:\"false\"\n")
                                Logger.appendToLog("Cloud Fix: Created new options.txt with renderClouds:\"false\"")
                            }
                            
                            Logging.i("LaunchGame", "Disabled clouds for MC $versionName (base: $baseVersion) to fix shader compatibility")
                        } else {
                            Logger.appendToLog("Cloud Fix: Version $baseVersion does not need cloud fix (patch < 5)")
                        }
                    } else {
                        Logger.appendToLog("Cloud Fix: Version $baseVersion does not need cloud fix (not 1.21+)")
                    }
                } else {
                    Logger.appendToLog("Cloud Fix: Could not parse version $baseVersion")
                }
            } catch (e: Exception) {
                Logger.appendToLog("Cloud Fix ERROR: ${e.message}")
                Logging.e("LaunchGame", "Failed to disable clouds", e)
            }
        }
    }
}
