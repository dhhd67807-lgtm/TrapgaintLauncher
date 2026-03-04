package com.movtery.zalithlauncher.feature.version.install

import android.app.Activity
import com.kdt.mcgui.ProgressLayout
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.event.value.InstallGameEvent
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.VersionInfo
import com.movtery.zalithlauncher.feature.version.VersionsManager
import com.movtery.zalithlauncher.task.Task
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader
import net.kdt.pojavlaunch.tasks.MinecraftDownloader
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class GameInstaller(
    private val activity: Activity,
    installEvent: InstallGameEvent
) {
    private val realVersion: String = installEvent.minecraftVersion
    private val customVersionName: String = installEvent.customVersionName
    private val taskMap: Map<Addon, InstallTaskItem> = installEvent.taskMap
    private val loaderType: String = installEvent.loaderType
    private val targetVersionFolder = VersionsManager.getVersionPath(customVersionName)
    private val vanillaVersionFolder = VersionsManager.getVersionPath(realVersion)

    companion object {
        @JvmField
        var currentInstallingLoader: String = "VANILLA"
    }

    fun installGame() {
        // Store the loader type for progress tracking
        currentInstallingLoader = loaderType
        
        Logging.i("Minecraft Downloader", "Start downloading the version: $realVersion (Loader: $loaderType)")
        Logging.i("Minecraft Downloader", "Custom version name: $customVersionName")
        Logging.i("Minecraft Downloader", "Task map size: ${taskMap.size}")

        if (taskMap.isNotEmpty()) {
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.download_install_download_file, 0, 0, 0)
        }

        val mcVersion = AsyncMinecraftDownloader.getListedVersion(realVersion)
        MinecraftDownloader().start(
            mcVersion,
            realVersion,
            object : AsyncMinecraftDownloader.DoneListener {
                override fun onDownloadDone() {
                    Task.runTask {
                        if (taskMap.isEmpty()) {
                            //如果附加附件是空的，则表明只需要安装原版，需要确保这个自定义的版本文件夹内必定有原版的.json文件
                            //需要检查是否自定义了版本名，如果真实版本与自定义用户名相同，则表示用户并没有修改版本名，当前安装的就是纯原版
                            //如果没有自定义用户名，则不复制版本文件，毕竟原版文件，与目标文件现在是同一个文件！
                            if (realVersion != customVersionName && VersionsManager.isVersionExists(realVersion)) {
                                //找到原版的.json文件，在MinecraftDownloader开始时，已经下载了
                                val vanillaJsonFile = File(vanillaVersionFolder, "${vanillaVersionFolder.name}.json")
                                if (vanillaJsonFile.exists() && vanillaJsonFile.isFile) {
                                    //如果原版的.json文件存在，则直接复制过来用
                                    FileUtils.copyFile(vanillaJsonFile, File(targetVersionFolder, "$customVersionName.json"))
                                }
                            }
                            
                            // Set the newly installed version as current
                            VersionsManager.saveCurrentVersion(customVersionName)
                            
                            // Refresh versions list to pick up the newly installed version
                            VersionsManager.refresh("GameInstaller")
                            
                            //ModLoader任务为空，接下来的无意义ModLoader任务将彻底跳过！
                            return@runTask null
                        }

                        //将Mod与Modloader的任务分离出来，应该先安装Mod
                        val modTask: MutableList<InstallTaskItem> = ArrayList()
                        val modloaderTask = AtomicReference<Pair<Addon, InstallTaskItem>>() //暂时只允许同时安装一个ModLoader
                        taskMap.forEach { (addon, taskItem) ->
                            if (taskItem.isMod) modTask.add(taskItem)
                            else modloaderTask.set(Pair(addon, taskItem))
                        }

                        //下载Mod文件
                        modTask.forEach { task ->
                            Logging.i("Install Version", "Installing Mod: ${task.selectedVersion}")
                            val file = task.task.run(customVersionName)
                            val endTask = task.endTask
                            file?.let { endTask?.endTask(activity, it) }
                        }

                        modloaderTask.get()?.let { taskPair ->
                            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.mod_download_progress, taskPair.first.addonName)

                            Logging.i("Install Version", "Installing ModLoader: ${taskPair.second.selectedVersion}")
                            val file = taskPair.second.task.run(customVersionName)
                            
                            // Save version info with loader type
                            saveVersionInfo(taskPair.first, taskPair.second.selectedVersion)
                            
                            return@runTask Pair(file, taskPair.second)
                        }

                        // If no modloader task, save vanilla version info
                        if (modloaderTask.get() == null) {
                            saveVersionInfo(null, null)
                        }

                        null
                    }.ended ended@{ taskPair ->
                        taskPair?.let { pair ->
                            pair.first?.let {
                                pair.second.endTask?.endTask(activity, it)
                            }
                        }
                        
                        // Ensure JSON file matches the custom version name
                        ensureJsonFileMatchesFolderName()
                        
                        // Set the newly installed version as current after all tasks complete
                        VersionsManager.saveCurrentVersion(customVersionName)
                        
                        // Refresh versions list to pick up the newly installed version
                        VersionsManager.refresh("GameInstaller")
                    }.onThrowable { e ->
                        Tools.showErrorRemote(e)
                    }.execute()
                }

                override fun onDownloadFailed(throwable: Throwable) {
                    Tools.showErrorRemote(throwable)
                    if (taskMap.isNotEmpty()) {
                        ProgressLayout.clearProgress(ProgressLayout.INSTALL_RESOURCE)
                    }
                }
            }
        )
    }
    
    private fun saveVersionInfo(addon: Addon?, loaderVersion: String?) {
        val loaderInfo = when {
            addon == Addon.FABRIC && loaderVersion != null -> arrayOf(
                VersionInfo.LoaderInfo("Fabric", loaderVersion)
            )
            addon == Addon.FORGE && loaderVersion != null -> arrayOf(
                VersionInfo.LoaderInfo("Forge", loaderVersion)
            )
            addon == Addon.NEOFORGE && loaderVersion != null -> arrayOf(
                VersionInfo.LoaderInfo("NeoForge", loaderVersion)
            )
            addon == Addon.QUILT && loaderVersion != null -> arrayOf(
                VersionInfo.LoaderInfo("Quilt", loaderVersion)
            )
            addon == Addon.OPTIFINE && loaderVersion != null -> arrayOf(
                VersionInfo.LoaderInfo("OptiFine", loaderVersion)
            )
            else -> null
        }
        
        val versionInfo = VersionInfo(realVersion, loaderInfo)
        versionInfo.save(targetVersionFolder)
        
        Logging.i("GameInstaller", "Saved version info for $customVersionName: ${versionInfo.getInfoString()}")
    }
    
    private fun ensureJsonFileMatchesFolderName() {
        // Make sure the JSON file name matches the folder name
        // This is required for VersionsManager to recognize the version as valid
        val expectedJsonFile = File(targetVersionFolder, "$customVersionName.json")
        
        if (!expectedJsonFile.exists()) {
            Logging.i("GameInstaller", "JSON file $customVersionName.json not found in target folder")
            
            // First, look for any JSON file in the target folder
            var foundJson = false
            targetVersionFolder.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json") && !file.name.equals("$customVersionName.json")) {
                    Logging.i("GameInstaller", "Found JSON file ${file.name}, renaming to $customVersionName.json")
                    file.renameTo(expectedJsonFile)
                    foundJson = true
                    return@forEach
                }
            }
            
            // If no JSON found and this is a Forge installation, copy from vanilla
            // Forge installer will update it when the game is first launched
            if (!foundJson && loaderType == "FORGE") {
                val vanillaJsonFile = File(vanillaVersionFolder, "$realVersion.json")
                if (vanillaJsonFile.exists()) {
                    Logging.i("GameInstaller", "Copying vanilla JSON for Forge from ${vanillaJsonFile.absolutePath}")
                    try {
                        FileUtils.copyFile(vanillaJsonFile, expectedJsonFile)
                        Logging.i("GameInstaller", "Successfully copied vanilla JSON for Forge installation")
                    } catch (e: Exception) {
                        Logging.e("GameInstaller", "Failed to copy vanilla JSON", e)
                    }
                } else {
                    Logging.e("GameInstaller", "Vanilla JSON file not found at ${vanillaJsonFile.absolutePath}")
                }
            } else if (!foundJson) {
                Logging.w("GameInstaller", "No JSON file found for $customVersionName")
            }
        } else {
            Logging.i("GameInstaller", "JSON file already exists: ${expectedJsonFile.absolutePath}")
        }
    }
}