package com.movtery.zalithlauncher.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.DialogModLoaderSelectionBinding
import com.movtery.zalithlauncher.feature.download.enums.Classify
import com.movtery.zalithlauncher.feature.download.enums.ModLoader
import com.movtery.zalithlauncher.feature.download.item.InfoItem
import com.movtery.zalithlauncher.feature.download.item.ModVersionItem
import com.movtery.zalithlauncher.feature.download.item.VersionItem
import com.movtery.zalithlauncher.feature.download.platform.AbstractPlatformHelper
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.VersionsManager
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils.Companion.setViewAnim
import com.movtery.anim.animations.Animations
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import java.util.Objects

class ModLoaderSelectionDialog(
    context: Context,
    private val infoItem: InfoItem,
    private val versions: List<VersionItem>,
    private val platformHelper: AbstractPlatformHelper
) : Dialog(context) {
    
    private lateinit var binding: DialogModLoaderSelectionBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogModLoaderSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Make dialog wider
        window?.apply {
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        binding.titleText.text = infoItem.title
        binding.cancelButton.setOnClickListener { dismiss() }
        
        if (infoItem.classify == Classify.MOD) {
            setupModLoaderOptions()
        } else {
            setupVersionOptions()
        }
    }
    
    // For mods: show Forge and Fabric options
    private fun setupModLoaderOptions() {
        // Get all compatible mod loaders from versions
        val compatibleModLoaders = mutableSetOf<ModLoader>()
        versions.forEach { versionItem ->
            if (versionItem is ModVersionItem) {
                compatibleModLoaders.addAll(versionItem.modloaders)
            }
        }
        
        // Filter to only show Forge and Fabric
        val allowedLoaders = compatibleModLoaders.filter { 
            it == ModLoader.FORGE || it == ModLoader.FABRIC 
        }
        
        // Get installed versions
        val installedVersions = VersionsManager.getVersions()
        
        // For each compatible mod loader, create an option
        allowedLoaders.forEach { modLoader ->
            val loaderView = LayoutInflater.from(context).inflate(
                R.layout.item_mod_loader_option,
                binding.loadersLayout,
                false
            )
            
            val loaderIcon = loaderView.findViewById<ImageView>(R.id.loader_icon)
            val loaderName = loaderView.findViewById<TextView>(R.id.loader_name)
            val versionsText = loaderView.findViewById<TextView>(R.id.versions_text)
            
            // Set loader icon and name
            val loaderInfo = getModLoaderInfo(modLoader)
            loaderIcon.setImageDrawable(ContextCompat.getDrawable(context, loaderInfo.first))
            loaderName.text = loaderInfo.second
            
            // Check for installed versions with this mod loader
            val compatibleVersions = installedVersions.filter { version ->
                version.getVersionInfo()?.loaderInfo?.any { loader ->
                    Objects.equals(modLoader.loaderName, loader.name)
                } ?: false
            }
            
            if (compatibleVersions.isEmpty()) {
                versionsText.text = "Download ${modLoader.loaderName} to continue"
                versionsText.setTextColor(ContextCompat.getColor(context, R.color.settings_category))
                loaderView.isEnabled = false
                loaderView.alpha = 0.5f
            } else {
                val versionNames = compatibleVersions.joinToString(", ") { it.getVersionName() }
                versionsText.text = "Installed: $versionNames"
                versionsText.setTextColor(ContextCompat.getColor(context, R.color.primary_text))
                
                loaderView.setOnClickListener {
                    // Let user select which version to install to
                    if (compatibleVersions.size == 1) {
                        downloadMostPopularVersion(modLoader, compatibleVersions[0].getVersionName())
                    } else {
                        showVersionSelectionDialog(modLoader, compatibleVersions)
                    }
                }
            }
            
            binding.loadersLayout.addView(loaderView)
        }
        
        // If no allowed loaders found
        if (allowedLoaders.isEmpty()) {
            val textView = TextView(context).apply {
                text = "No compatible mod loaders found"
                setPadding(32, 32, 32, 32)
                setTextColor(ContextCompat.getColor(context, R.color.settings_category))
            }
            binding.loadersLayout.addView(textView)
        }
    }
    
    // For modpacks, resource packs, shader packs: show all installed versions with icons
    private fun setupVersionOptions() {
        val installedVersions = VersionsManager.getVersions()
        
        if (installedVersions.isEmpty()) {
            val textView = TextView(context).apply {
                text = "No versions installed"
                setPadding(32, 32, 32, 32)
                setTextColor(ContextCompat.getColor(context, R.color.settings_category))
            }
            binding.loadersLayout.addView(textView)
            return
        }
        
        installedVersions.forEach { version ->
            val loaderView = LayoutInflater.from(context).inflate(
                R.layout.item_mod_loader_option,
                binding.loadersLayout,
                false
            )
            
            val loaderIcon = loaderView.findViewById<ImageView>(R.id.loader_icon)
            val loaderName = loaderView.findViewById<TextView>(R.id.loader_name)
            val versionsText = loaderView.findViewById<TextView>(R.id.versions_text)
            
            // Determine loader type and set icon/color
            val loaderInfo = version.getVersionInfo()?.loaderInfo
            val versionName = version.getVersionName()
            val mcVersion = version.getVersionInfo()?.minecraftVersion
            
            if (loaderInfo.isNullOrEmpty()) {
                // Vanilla
                loaderIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_minecraft))
                loaderName.text = versionName
                loaderName.setTextColor(ContextCompat.getColor(context, R.color.vanilla_color))
                versionsText.text = "Vanilla"
            } else {
                val loader = loaderInfo[0]
                when {
                    loader.name.contains("forge", ignoreCase = true) -> {
                        loaderIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_anvil))
                        loaderName.text = versionName
                        loaderName.setTextColor(ContextCompat.getColor(context, R.color.forge_color))
                        versionsText.text = "Forge"
                    }
                    loader.name.contains("fabric", ignoreCase = true) -> {
                        loaderIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_fabric))
                        loaderName.text = versionName
                        loaderName.setTextColor(ContextCompat.getColor(context, R.color.fabric_color))
                        versionsText.text = "Fabric"
                    }
                    else -> {
                        loaderIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_minecraft))
                        loaderName.text = versionName
                        loaderName.setTextColor(ContextCompat.getColor(context, R.color.primary_text))
                        versionsText.text = loader.name
                    }
                }
            }
            
            versionsText.setTextColor(ContextCompat.getColor(context, R.color.settings_category))
            
            loaderView.setOnClickListener {
                // Auto-download most popular compatible version
                downloadMostPopularVersionForMC(mcVersion)
            }
            
            binding.loadersLayout.addView(loaderView)
        }
    }
    
    private fun downloadMostPopularVersion(modLoader: ModLoader, targetVersion: String) {
        // Find the most downloaded version compatible with this mod loader and MC version
        val compatibleVersions = versions.filter { versionItem ->
            if (versionItem !is ModVersionItem) return@filter false
            
            // Check if this version supports the mod loader
            val hasLoader = versionItem.modloaders.contains(modLoader)
            if (!hasLoader) return@filter false
            
            // Check if this version supports the target MC version
            val targetMCVersion = VersionsManager.getVersions()
                .find { it.getVersionName() == targetVersion }
                ?.getVersionInfo()?.minecraftVersion
            
            targetMCVersion != null && versionItem.mcVersions.contains(targetMCVersion)
        }.sortedByDescending { it.downloadCount }
        
        if (compatibleVersions.isEmpty()) {
            Toast.makeText(context, "No compatible versions found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val mostPopular = compatibleVersions.first()
        startInstall(mostPopular)
    }
    
    private fun downloadMostPopularVersionForMC(mcVersion: String?) {
        if (mcVersion == null) {
            Toast.makeText(context, "Unable to determine MC version", Toast.LENGTH_SHORT).show()
            return
        }
        
        // For shader packs and resource packs, they work with any version
        // Just find the most downloaded version overall if no MC version match
        val compatibleVersions = versions.filter { versionItem ->
            versionItem.mcVersions.contains(mcVersion)
        }.sortedByDescending { it.downloadCount }
        
        val versionToDownload = if (compatibleVersions.isNotEmpty()) {
            compatibleVersions.first()
        } else {
            // If no exact match, get the most popular version overall
            versions.sortedByDescending { it.downloadCount }.firstOrNull()
        }
        
        if (versionToDownload == null) {
            Toast.makeText(context, "No versions found", Toast.LENGTH_SHORT).show()
            return
        }
        
        startInstall(versionToDownload)
    }
    
    private fun startInstall(versionItem: VersionItem) {
        // Skip dependencies dialog and install directly
        platformHelper.install(context, infoItem, versionItem) { key ->
            val containsProgress = ProgressKeeper.containsProgress(key)
            if (containsProgress) {
                Toast.makeText(context, context.getString(R.string.tasks_ongoing), Toast.LENGTH_SHORT).show()
            }
            false // Always return false to allow installation
        }
        
        Toast.makeText(context, "Downloading ${versionItem.title}", Toast.LENGTH_SHORT).show()
        dismiss()
    }
    
    private fun showVersionSelectionDialog(modLoader: ModLoader, versions: List<com.movtery.zalithlauncher.feature.version.Version>) {
        val versionNames = versions.map { it.getVersionName() }.toTypedArray()
        
        android.app.AlertDialog.Builder(context)
            .setTitle("Select Version")
            .setItems(versionNames) { _, which ->
                val selectedVersion = versionNames[which]
                downloadMostPopularVersion(modLoader, selectedVersion)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun getModLoaderInfo(modLoader: ModLoader): Pair<Int, String> {
        return when (modLoader) {
            ModLoader.FORGE -> Pair(R.drawable.ic_anvil, "Forge")
            ModLoader.FABRIC -> Pair(R.drawable.ic_fabric, "Fabric")
            else -> Pair(R.drawable.ic_minecraft, "Unknown")
        }
    }
}
