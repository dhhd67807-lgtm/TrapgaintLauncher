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
import com.movtery.zalithlauncher.feature.log.Logging
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
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import net.kdt.pojavlaunch.plugins.FFmpegPlugin
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.services.GameService
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader
import net.kdt.pojavlaunch.tasks.MinecraftDownloader
import net.kdt.pojavlaunch.utils.JREUtils
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.greenrobot.eventbus.EventBus

class LaunchGame {
    companion object {
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
                    version.id?.let { versionId ->
                        val parts = versionId.split(".")
                        if (parts.size >= 2) {
                            val major = parts[0].toIntOrNull() ?: 0
                            val minor = parts[1].substringBefore("-").toIntOrNull() ?: 0
                            when {
                                major > 1 -> 21 // Future versions (2.x+)
                                major == 1 && minor >= 21 -> 21 // 1.21+ requires Java 21
                                major == 1 && minor >= 20 -> 21 // 1.20.5+ also needs Java 21
                                major == 1 && minor >= 18 -> 17 // 1.18-1.20.4 needs Java 17
                                major == 1 && minor >= 17 -> 17 // 1.17 needs Java 17
                                else -> 8
                            }
                        } else 8
                    } ?: 8
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
            
            // Check renderer compatibility for Minecraft 1.21.8+
            val currentRenderer = Renderers.getCurrentRenderer()
            if (targetJavaVersion >= 21) {
                val parts = version.id?.split(".") ?: emptyList()
                if (parts.size >= 3) {
                    val minor = parts[1].toIntOrNull() ?: 0
                    val patch = parts[2].substringBefore("-").toIntOrNull() ?: 0
                    if (minor == 21 && patch >= 8 && currentRenderer.getRendererId() == "opengles2") {
                        Logger.appendToLog("WARNING: Minecraft ${version.id} may not work properly with GL4ES renderer")
                        Logger.appendToLog("RECOMMENDATION: Switch to Vulkan/Zink renderer for better compatibility")
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "Warning: MC ${version.id} may crash with GL4ES. Try Vulkan/Zink renderer instead.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

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
            Tools.disableSplash(gameDirPath)
            
            // Copy user skin to game directory for offline accounts
            copySkinToGameDir(account, gameDirPath)
            
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
    }
}