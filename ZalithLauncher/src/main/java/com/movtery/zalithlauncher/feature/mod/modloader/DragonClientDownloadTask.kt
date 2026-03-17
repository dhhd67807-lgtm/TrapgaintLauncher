package com.movtery.zalithlauncher.feature.mod.modloader

import com.kdt.mcgui.ProgressLayout
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.version.install.InstallTask
import com.movtery.zalithlauncher.utils.file.FileTools
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.utils.DownloadUtils
import java.io.File
import java.io.IOException
import java.util.Locale

class DragonClientDownloadTask(
    private val asset: DragonClientManifestResolver.DragonClientAsset
) : InstallTask, Tools.DownloaderFeedback {
    @Throws(Exception::class)
    override fun run(customName: String): File {
        val destinationFile = File(PathManager.DIR_CACHE, asset.fileName)
        ProgressKeeper.submitProgress(
            ProgressLayout.INSTALL_RESOURCE,
            0,
            R.string.mod_download_progress,
            asset.fileName
        )

        try {
            if (!destinationFile.exists() || !verifySha256(destinationFile)) {
                DownloadUtils.downloadFileMonitored(asset.downloadUrl, destinationFile, ByteArray(8192), this)
            }

            if (!verifySha256(destinationFile)) {
                destinationFile.delete()
                throw IOException("Dragon Client checksum mismatch for ${asset.fileName}")
            }
            return destinationFile
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_RESOURCE)
        }
    }

    override fun updateProgress(curr: Long, max: Long) {
        val progress100 = if (max <= 0L) 0 else ((curr.toFloat() / max.toFloat()) * 100f).toInt()
        ProgressKeeper.submitProgress(
            ProgressLayout.INSTALL_RESOURCE,
            progress100,
            R.string.mod_download_progress,
            asset.fileName
        )
    }

    @Throws(Exception::class)
    private fun verifySha256(file: File): Boolean {
        if (asset.sha256.isBlank()) return true
        val calculated = FileTools.calculateFileHash(file, "SHA-256")
        return calculated.lowercase(Locale.ROOT) == asset.sha256.lowercase(Locale.ROOT)
    }
}
