package com.movtery.zalithlauncher.ui.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentVersionBinding
import com.movtery.zalithlauncher.event.value.InstallGameEvent
import com.movtery.zalithlauncher.feature.download.enums.Classify
import com.movtery.zalithlauncher.feature.download.enums.Platform
import com.movtery.zalithlauncher.feature.download.enums.VersionType as DownloadVersionType
import com.movtery.zalithlauncher.feature.download.item.VersionItem
import com.movtery.zalithlauncher.feature.download.platform.modrinth.ModrinthCommonUtils
import com.movtery.zalithlauncher.feature.mod.modloader.DragonClientDownloadTask
import com.movtery.zalithlauncher.feature.mod.modloader.DragonClientManifestResolver
import com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeApiModDownloadTask
import com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeDownloadTask
import com.movtery.zalithlauncher.feature.mod.modloader.OptiFineDownloadTask
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.feature.version.install.Addon
import com.movtery.zalithlauncher.feature.version.install.InstallArgsUtils
import com.movtery.zalithlauncher.feature.version.install.InstallTaskItem
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.subassembly.versionlist.VersionSelectedListener
import com.movtery.zalithlauncher.ui.subassembly.versionlist.VersionType
import com.movtery.zalithlauncher.utils.LauncherProfiles
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.setDebouncedClickListener
import com.movtery.zalithlauncher.utils.runtime.SelectRuntimeUtils
import net.kdt.pojavlaunch.JavaGUILauncherActivity
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.ForgeUtils
import net.kdt.pojavlaunch.modloaders.OptiFineUtils
import org.apache.commons.io.FileUtils
import org.greenrobot.eventbus.EventBus
import java.io.File

class VersionSelectorFragment : FragmentWithAnim(R.layout.fragment_version) {
    companion object {
        const val TAG: String = "FileSelectorFragment"
        val DRAGON_SUPPORTED_VERSIONS = listOf(
            "1.21.1",
            "1.21.3",
            "1.21.4",
            "1.21.6",
            "1.21.7",
            "1.21.8",
            "1.21.10",
            "1.21.11"
        )
    }

    private lateinit var binding: FragmentVersionBinding
    private var release: TabLayout.Tab? = null
    private var snapshot: TabLayout.Tab? = null
    private var beta: TabLayout.Tab? = null
    private var alpha: TabLayout.Tab? = null
    private var versionType: VersionType? = null
    private var loaderType: String = "VANILLA"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVersionBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindTab()
        
        // Get loader type and selected version from arguments
        loaderType = arguments?.getString("LOADER_TYPE") ?: "VANILLA"
        val selectedVersion = arguments?.getString("SELECTED_VERSION")

        binding.apply {
            // Set the icon based on loader type
            val loaderIcon = when (loaderType) {
                "VANILLA" -> R.drawable.ic_vanilla
                "FABRIC" -> R.drawable.ic_fabric_loader
                "FORGE" -> R.drawable.ic_forge
                "CUSTOM" -> R.drawable.dragon_logo
                else -> R.drawable.ic_vanilla
            }
            
            // Update version list to use the loader icon
            version.setLoaderIcon(loaderIcon)
            version.setLoaderType(loaderType)
            
            if (loaderType == "CUSTOM") {
                version.setVersionFilter("1.21")
                version.setAllowedVersionIds(DRAGON_SUPPORTED_VERSIONS)
            } else {
                version.setAllowedVersionIds(null)
                // Set version filter if a specific version was selected (e.g., "1.21")
                if (selectedVersion != null) {
                    version.setVersionFilter(selectedVersion)
                }
            }

            refresh(versionTab.getTabAt(versionTab.selectedTabPosition))

            versionTab.addOnTabSelectedListener(object : OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    refresh(tab)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                }
            })

            searchVersion.doAfterTextChanged { text ->
                val string = text?.toString() ?: ""
                version.setFilterString(string)
            }

            returnButton.setDebouncedClickListener { ZHTools.onBackPressed(requireActivity()) }

            version.setVersionSelectedListener(object : VersionSelectedListener() {
                override fun onVersionSelected(version: String?) {
                    if (version == null) {
                        Tools.backToMainMenu(requireActivity())
                    } else {
                        // Auto-install with the appropriate loader based on loaderType
                        when (loaderType) {
                            "VANILLA" -> startAutoInstallWithOptiFine(version)
                            "FABRIC" -> startAutoInstallWithFabric(version)
                            "FORGE" -> startAutoInstallWithForge(version)
                            "CUSTOM" -> {
                                if (!DRAGON_SUPPORTED_VERSIONS.contains(version)) {
                                    Toast.makeText(requireActivity(), "Dragon supports only selected 1.21.x builds", Toast.LENGTH_LONG).show()
                                    return
                                }
                                startAutoInstallWithCustom(version)
                            }
                            else -> startAutoInstallWithOptiFine(version)
                        }
                    }
                }
            })
        }
    }
    
    private fun startAutoInstallWithOptiFine(mcVersion: String) {
        // Get activity reference before navigating away
        val activity = requireActivity()
        
        // Navigate back to home immediately
        Tools.backToMainMenu(activity)
        
        // Fetch OptiFine versions in background
        TaskExecutors.getDefault().submit {
            try {
                val optiFineVersions = OptiFineUtils.downloadOptiFineVersions(false)
                
                // Find OptiFine versions for this MC version
                val mcOptiFineVersions = mutableListOf<OptiFineUtils.OptiFineVersion>()
                optiFineVersions?.optifineVersions?.forEach { versionList: List<OptiFineUtils.OptiFineVersion> ->
                    versionList.forEach { optiFineVersion: OptiFineUtils.OptiFineVersion ->
                        val cleanMcVersion = optiFineVersion.minecraftVersion.removePrefix("Minecraft").trim()
                        if (cleanMcVersion == mcVersion) {
                            mcOptiFineVersions.add(optiFineVersion)
                        }
                    }
                }
                
                if (mcOptiFineVersions.isEmpty()) {
                    // No OptiFine available, install vanilla only
                    EventBus.getDefault().post(
                        InstallGameEvent(
                            mcVersion,
                            mcVersion,
                            emptyMap(),
                            "VANILLA"
                        )
                    )
                } else {
                    // Use the first (latest) OptiFine version
                    val latestOptiFine = mcOptiFineVersions[0]
                    val customVersionName = "$mcVersion OptiFine"
                    
                    // Create task map with OptiFine
                    val taskMap = mapOf(
                        Addon.OPTIFINE to InstallTaskItem(
                            latestOptiFine.versionName,
                            false,
                            OptiFineDownloadTask(latestOptiFine),
                            null
                        )
                    )
                    
                    // Post installation event
                    EventBus.getDefault().post(
                        InstallGameEvent(
                            mcVersion,
                            customVersionName,
                            taskMap,
                            "VANILLA"
                        )
                    )
                }
            } catch (e: Exception) {
                // Install vanilla as fallback
                EventBus.getDefault().post(
                    InstallGameEvent(
                        mcVersion,
                        mcVersion,
                        emptyMap(),
                        "VANILLA"
                    )
                )
            }
        }
    }
    
    private fun startAutoInstallWithFabric(mcVersion: String) {
        val activity = requireActivity()
        Tools.backToMainMenu(activity)
        
        android.util.Log.d("VersionSelector", "Starting Fabric installation for MC version: $mcVersion")
        
        TaskExecutors.getDefault().submit {
            try {
                val fabricUtils = com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeUtils.FABRIC_UTILS
                var gameVersions = fabricUtils.downloadGameVersions(false)
                var loaderVersions = fabricUtils.downloadLoaderVersions(false)

                if (gameVersions.isNullOrEmpty()) {
                    gameVersions = fabricUtils.downloadGameVersions(true)
                }
                if (loaderVersions.isNullOrEmpty()) {
                    loaderVersions = fabricUtils.downloadLoaderVersions(true)
                }

                android.util.Log.d("VersionSelector", "Fabric game versions count: ${gameVersions?.size ?: 0}")
                android.util.Log.d("VersionSelector", "Fabric loader versions count: ${loaderVersions?.size ?: 0}")

                val mcSupported = gameVersions?.any { it.version == mcVersion } == true
                if (!mcSupported || loaderVersions.isNullOrEmpty()) {
                    android.util.Log.w("VersionSelector", "Fabric metadata unavailable for MC $mcVersion (supported=$mcSupported, loaders=${loaderVersions?.size ?: 0})")
                    TaskExecutors.runInUIThread {
                        Toast.makeText(activity, "Fabric metadata unavailable for $mcVersion. Please try again.", Toast.LENGTH_LONG).show()
                    }
                    return@submit
                }

                // Use the first stable loader, fallback to the newest available loader.
                val latestFabric = loaderVersions.firstOrNull { it.stable } ?: loaderVersions[0]
                val customVersionName = "$mcVersion Fabric"

                android.util.Log.d("VersionSelector", "Installing Fabric version: ${latestFabric.version} for MC $mcVersion")

                val fabricTask = FabricLikeDownloadTask(fabricUtils, mcVersion, latestFabric.version)
                val taskMap = mapOf(
                    Addon.FABRIC to InstallTaskItem(
                        latestFabric.version,
                        false,
                        fabricTask,
                        null
                    )
                )

                EventBus.getDefault().post(InstallGameEvent(mcVersion, customVersionName, taskMap, "FABRIC"))
            } catch (e: Exception) {
                android.util.Log.e("VersionSelector", "Error installing Fabric", e)
                TaskExecutors.runInUIThread {
                    Toast.makeText(activity, "Failed to install Fabric: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startAutoInstallWithCustom(mcVersion: String) {
        val activity = requireActivity()
        Tools.backToMainMenu(activity)

        TaskExecutors.getDefault().submit {
            try {
                val fabricUtils = com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeUtils.FABRIC_UTILS
                var gameVersions = fabricUtils.downloadGameVersions(false)
                var loaderVersions = fabricUtils.downloadLoaderVersions(false)

                if (gameVersions.isNullOrEmpty()) gameVersions = fabricUtils.downloadGameVersions(true)
                if (loaderVersions.isNullOrEmpty()) loaderVersions = fabricUtils.downloadLoaderVersions(true)

                val mcSupported = gameVersions?.any { it.version == mcVersion } == true
                if (!mcSupported || loaderVersions.isNullOrEmpty()) {
                    TaskExecutors.runInUIThread {
                        Toast.makeText(activity, "Fabric metadata unavailable for $mcVersion", Toast.LENGTH_LONG).show()
                    }
                    return@submit
                }

                val latestFabric = loaderVersions.firstOrNull { it.stable } ?: loaderVersions[0]
                val fabricApiVersion = findLatestFabricApiForVersion(mcVersion)
                if (fabricApiVersion == null) {
                    TaskExecutors.runInUIThread {
                        Toast.makeText(activity, "Fabric API not found for $mcVersion on Modrinth", Toast.LENGTH_LONG).show()
                    }
                    return@submit
                }

                val dragonAsset = DragonClientManifestResolver.resolveLatestAsset(mcVersion)
                if (dragonAsset == null) {
                    TaskExecutors.runInUIThread {
                        Toast.makeText(activity, "Dragon Client not available for $mcVersion", Toast.LENGTH_LONG).show()
                    }
                    return@submit
                }

                val customVersionName = "$mcVersion Dragon Client"
                val fabricTask = FabricLikeDownloadTask(fabricUtils, mcVersion, latestFabric.version)
                val taskMap = linkedMapOf(
                    Addon.FABRIC to InstallTaskItem(
                        latestFabric.version,
                        false,
                        fabricTask,
                        null
                    ),
                    Addon.FABRIC_API to InstallTaskItem(
                        fabricApiVersion.title,
                        true,
                        FabricLikeApiModDownloadTask("fabric-api", fabricApiVersion),
                        createMoveToModsEndTask(customVersionName, "fabric-api-${mcVersion}.jar")
                    ),
                    Addon.DRAGON_CLIENT to InstallTaskItem(
                        "Dragon Client ${dragonAsset.clientVersion}",
                        true,
                        DragonClientDownloadTask(dragonAsset),
                        createMoveToModsEndTask(customVersionName, dragonAsset.fileName)
                    )
                )

                EventBus.getDefault().post(InstallGameEvent(mcVersion, customVersionName, taskMap, "CUSTOM"))
            } catch (e: Exception) {
                android.util.Log.e("VersionSelector", "Error installing custom loader", e)
                TaskExecutors.runInUIThread {
                    Toast.makeText(activity, "Custom install failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun startAutoInstallWithForge(mcVersion: String) {
        val activity = requireActivity()
        Tools.backToMainMenu(activity)
        
        android.util.Log.d("VersionSelector", "Starting Forge installation for MC version: $mcVersion")
        
        TaskExecutors.getDefault().submit {
            try {
                // For Forge, we need to download the version list and find a compatible version
                var forgeVersions: List<String>? = ForgeUtils.downloadForgeVersions(false)
                
                android.util.Log.d("VersionSelector", "Forge versions count: ${forgeVersions?.size ?: 0}")

                // Refresh metadata once when cache is stale or empty.
                if (forgeVersions.isNullOrEmpty()) {
                    forgeVersions = ForgeUtils.downloadForgeVersions(true)
                    android.util.Log.d("VersionSelector", "Forge versions after force refresh: ${forgeVersions?.size ?: 0}")
                }
                
                if (forgeVersions.isNullOrEmpty()) {
                    android.util.Log.w("VersionSelector", "No Forge versions found for MC $mcVersion")
                    TaskExecutors.runInUIThread {
                        Toast.makeText(activity, "No Forge version found for $mcVersion", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Find the first Forge version that matches this MC version
                    // Prefer the newest compatible version instead of the oldest one.
                    val compatibleForge: String? = forgeVersions.lastOrNull { version: String ->
                        version.startsWith("$mcVersion-")
                    }
                    
                    android.util.Log.d("VersionSelector", "Compatible Forge version: $compatibleForge")
                    
                    if (compatibleForge != null) {
                        val customVersionName = "$mcVersion Forge"
                        val forgeTask = com.movtery.zalithlauncher.feature.mod.modloader.ForgeDownloadTask(compatibleForge)
                        
                        val taskMap = mapOf(
                            Addon.FORGE to InstallTaskItem(
                                compatibleForge,
                                false,
                                forgeTask,
                                createGuiInstallerEndTask(Addon.FORGE.addonName, mcVersion, compatibleForge) { intent, argUtils, installerFile ->
                                    argUtils.setForge(intent, installerFile, customVersionName)
                                }
                            )
                        )
                        
                        EventBus.getDefault().post(InstallGameEvent(mcVersion, customVersionName, taskMap, "FORGE"))
                    } else {
                        android.util.Log.w("VersionSelector", "No compatible Forge version found for MC $mcVersion")
                        TaskExecutors.runInUIThread {
                            Toast.makeText(activity, "No compatible Forge build for $mcVersion", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VersionSelector", "Error installing Forge", e)
                TaskExecutors.runInUIThread {
                    Toast.makeText(activity, "Failed to install Forge: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createGuiInstallerEndTask(
        addonName: String,
        mcVersion: String,
        loaderVersion: String,
        setArgs: (Intent, InstallArgsUtils, java.io.File) -> Unit
    ): InstallTaskItem.EndTask {
        return InstallTaskItem.EndTask { taskActivity, installerFile ->
            val intent = Intent(taskActivity, JavaGUILauncherActivity::class.java)
            val argUtils = InstallArgsUtils(mcVersion, loaderVersion)
            setArgs(intent, argUtils, installerFile)

            SelectRuntimeUtils.selectRuntime(
                taskActivity,
                taskActivity.getString(R.string.version_install_new_modloader, addonName)
            ) { jreName ->
                LauncherProfiles.generateLauncherProfiles()
                intent.putExtra(JavaGUILauncherActivity.EXTRAS_JRE_NAME, jreName)
                taskActivity.startActivity(intent)
            }
        }
    }

    private fun findLatestFabricApiForVersion(mcVersion: String): VersionItem? {
        val helper = Platform.MODRINTH.helper
        val infoItem = ModrinthCommonUtils.getInfo(helper.api, Classify.MOD, "P7dR8mSH") ?: return null

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

    private fun createMoveToModsEndTask(customVersionName: String, targetFileName: String): InstallTaskItem.EndTask {
        return InstallTaskItem.EndTask { _, downloadedFile ->
            val modsDir = if (AllSettings.versionIsolation.getValue()) {
                File(
                    ProfilePathHome.getGameHome(),
                    "versions${File.separator}$customVersionName${File.separator}mods"
                )
            } else {
                File(ProfilePathHome.getGameHome(), "mods")
            }

            if (!modsDir.exists()) modsDir.mkdirs()
            val destinationFile = File(modsDir, targetFileName)
            if (destinationFile.exists()) destinationFile.delete()
            FileUtils.copyFile(downloadedFile, destinationFile)
        }
    }

    private fun refresh(tab: TabLayout.Tab?) {
        binding.apply {
            setVersionType(tab)
            version.setVersionType(versionType)
        }
    }

    private fun setVersionType(tab: TabLayout.Tab?) {
        versionType = when (tab) {
            release -> VersionType.RELEASE
            snapshot -> VersionType.SNAPSHOT
            beta -> VersionType.BETA
            alpha -> VersionType.ALPHA
            else -> VersionType.RELEASE
        }
    }

    private fun bindTab() {
        binding.apply {
            release = versionTab.newTab().setText(R.string.generic_release)
            snapshot = versionTab.newTab().setText(R.string.version_snapshot)
            beta = versionTab.newTab().setText(R.string.version_beta)
            alpha = versionTab.newTab().setText(R.string.version_alpha)

            versionTab.addTab(release!!)
            versionTab.addTab(snapshot!!)
            versionTab.addTab(beta!!)
            versionTab.addTab(alpha!!)

            versionTab.selectTab(release)
        }
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.versionLayout, Animations.BounceInDown))
            .apply(AnimPlayer.Entry(binding.operateLayout, Animations.BounceInLeft))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.versionLayout, Animations.FadeOutUp))
            .apply(AnimPlayer.Entry(binding.operateLayout, Animations.FadeOutRight))
    }
}
