package com.movtery.zalithlauncher.feature.mod.modloader;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome;
import com.movtery.zalithlauncher.feature.version.install.InstallTask;
import com.movtery.zalithlauncher.utils.path.PathManager;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.ForgeUtils;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ForgeDownloadTask implements InstallTask, Tools.DownloaderFeedback {
    private String mDownloadUrl;
    private String mFullVersion;
    private String mLoaderVersion;
    private String mGameVersion;

    public ForgeDownloadTask(String forgeVersion) {
        this.mDownloadUrl = ForgeUtils.getInstallerUrl(forgeVersion);
        this.mFullVersion = forgeVersion;
    }

    public ForgeDownloadTask(String gameVersion, String loaderVersion) {
        this.mLoaderVersion = loaderVersion;
        this.mGameVersion = gameVersion;
    }

    @Override
    public File run(@NonNull String customName) throws Exception {
        File outputFile = null;
        if (mGameVersion != null && mLoaderVersion != null) {
            // Use automatic install method - download installer and extract JSON
            legacyInstall(customName);
            outputFile = null;
        } else if (determineDownloadUrl()) {
            outputFile = downloadForge();
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_RESOURCE);
        return outputFile;
    }
    
    private void legacyInstall(String customName) throws Exception {
        // First, find the full Forge version
        if (!findVersion()) {
            throw new IOException("Forge version not found");
        }
        
        android.util.Log.i("ForgeDownloadTask", "Installing Forge " + mFullVersion + " to " + customName);
        
        // Download the Forge installer JAR
        File installerFile = new File(PathManager.DIR_CACHE, "forge-installer-" + mFullVersion + ".jar");
        String installerUrl = ForgeUtils.getInstallerUrl(mFullVersion);
        
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.mod_download_progress, "Forge Installer");
        byte[] buffer = new byte[8192];
        DownloadUtils.downloadFileMonitored(installerUrl, installerFile, buffer, this);
        
        // Extract version.json and install.profile from the installer JAR
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 50, R.string.mod_download_progress, "Extracting Forge");
        extractAndInstallForge(installerFile, customName);
        
        // Clean up installer file
        installerFile.delete();
        
        android.util.Log.i("ForgeDownloadTask", "Forge installation completed for " + customName);
    }
    
    private void extractAndInstallForge(File installerJar, String customName) throws Exception {
        File versionDir = new File(ProfilePathHome.getVersionsHome(), customName);
        FileUtils.ensureDirectory(versionDir);
        
        File versionJson = new File(versionDir, customName + ".json");
        
        try (ZipFile zipFile = new ZipFile(installerJar)) {
            // Try to find version.json in the installer
            ZipEntry versionEntry = zipFile.getEntry("version.json");
            if (versionEntry == null) {
                // Try alternate location
                versionEntry = zipFile.getEntry("install_profile.json");
            }
            
            if (versionEntry != null) {
                // Extract and process the JSON
                try (InputStream is = zipFile.getInputStream(versionEntry)) {
                    String jsonContent = new String(readAllBytes(is));
                    
                    // If it's install_profile.json, extract the version info from it
                    if (versionEntry.getName().equals("install_profile.json")) {
                        JSONObject profile = new JSONObject(jsonContent);
                        if (profile.has("versionInfo")) {
                            jsonContent = profile.getJSONObject("versionInfo").toString();
                        } else if (profile.has("version")) {
                            jsonContent = profile.getJSONObject("version").toString();
                        }
                    }
                    
                    // Fix library URLs - add Forge Maven repository for libraries without URLs
                    jsonContent = fixForgeLibraryUrls(jsonContent);
                    
                    // Write the version JSON
                    Tools.write(versionJson.getAbsolutePath(), jsonContent);
                    android.util.Log.i("ForgeDownloadTask", "Extracted Forge version JSON to " + versionJson.getAbsolutePath());
                }
            } else {
                throw new IOException("Could not find version.json or install_profile.json in Forge installer");
            }
            
            // Extract Forge libraries if present
            extractForgeLibraries(zipFile, versionDir);
        }
    }
    
    private String fixForgeLibraryUrls(String jsonContent) {
        try {
            JSONObject versionJson = new JSONObject(jsonContent);
            if (versionJson.has("libraries")) {
                org.json.JSONArray libraries = versionJson.getJSONArray("libraries");
                org.json.JSONArray newLibraries = new org.json.JSONArray();
                
                for (int i = 0; i < libraries.length(); i++) {
                    JSONObject lib = libraries.getJSONObject(i);
                    
                    if (!lib.has("name")) {
                        newLibraries.put(lib);
                        continue;
                    }
                    
                    String name = lib.getString("name");
                    
                    // Skip Forge client/universal artifacts - they should be extracted from installer
                    // But only if they don't have a valid URL
                    if (name.contains(":forge:") && (name.endsWith(":client") || name.endsWith(":universal"))) {
                        boolean hasValidUrl = false;
                        if (lib.has("downloads")) {
                            JSONObject downloads = lib.getJSONObject("downloads");
                            if (downloads.has("artifact")) {
                                JSONObject artifact = downloads.getJSONObject("artifact");
                                if (artifact.has("url") && !artifact.getString("url").isEmpty()) {
                                    hasValidUrl = true;
                                }
                            }
                        }
                        
                        if (!hasValidUrl) {
                            android.util.Log.d("ForgeDownloadTask", "Skipping Forge artifact (will be extracted from installer): " + name);
                            continue;
                        }
                    }
                    
                    // Check if library has downloads section with valid artifact URL
                    boolean needsUrl = false;
                    if (!lib.has("downloads")) {
                        needsUrl = true;
                    } else {
                        JSONObject downloads = lib.getJSONObject("downloads");
                        if (!downloads.has("artifact")) {
                            needsUrl = true;
                        } else {
                            JSONObject artifact = downloads.getJSONObject("artifact");
                            if (!artifact.has("url") || artifact.getString("url").isEmpty()) {
                                needsUrl = true;
                            }
                        }
                    }
                    
                    if (needsUrl) {
                        String[] parts = name.split(":");
                        if (parts.length >= 3) {
                            String group = parts[0].replace('.', '/');
                            String artifact = parts[1];
                            String version = parts[2];
                            String classifier = parts.length > 3 ? "-" + parts[3] : "";
                            
                            String path = group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
                            
                            // Try multiple Maven repositories
                            String url;
                            if (name.startsWith("net.minecraftforge") || name.startsWith("cpw.mods")) {
                                url = "https://maven.minecraftforge.net/" + path;
                            } else if (name.startsWith("org.ow2.asm")) {
                                url = "https://repo1.maven.org/maven2/" + path;
                            } else {
                                // Default to Forge Maven
                                url = "https://maven.minecraftforge.net/" + path;
                            }
                            
                            // Create or update downloads section
                            JSONObject downloads = lib.has("downloads") ? lib.getJSONObject("downloads") : new JSONObject();
                            JSONObject artifactObj = downloads.has("artifact") ? downloads.getJSONObject("artifact") : new JSONObject();
                            artifactObj.put("url", url);
                            if (!artifactObj.has("path")) {
                                artifactObj.put("path", path);
                            }
                            downloads.put("artifact", artifactObj);
                            lib.put("downloads", downloads);
                            
                            android.util.Log.d("ForgeDownloadTask", "Added URL for library: " + name + " -> " + url);
                        }
                    }
                    
                    newLibraries.put(lib);
                }
                
                versionJson.put("libraries", newLibraries);
            }
            return versionJson.toString();
        } catch (Exception e) {
            android.util.Log.e("ForgeDownloadTask", "Failed to fix library URLs", e);
            return jsonContent; // Return original if fixing fails
        }
    }
    
    private void extractForgeLibraries(ZipFile zipFile, File versionDir) {
        try {
            // Look for maven/ directory in the installer
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // Extract files from maven/ directory to libraries/
                if (name.startsWith("maven/") && !entry.isDirectory()) {
                    String libPath = name.substring(6); // Remove "maven/" prefix
                    File libFile = new File(ProfilePathHome.getGameHome(), "libraries/" + libPath);
                    
                    FileUtils.ensureParentDirectory(libFile);
                    
                    try (InputStream is = zipFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(libFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    
                    android.util.Log.d("ForgeDownloadTask", "Extracted library: " + libPath);
                }
            }
            
            // Also look for Forge client/universal JAR in the installer root
            // These are typically named like "forge-1.21.8-58.0.3-client.jar" or "forge-1.21.8-58.0.3-universal.jar"
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // Look for forge client/universal JARs in root or data/ directory
                if (!entry.isDirectory() && (name.endsWith("-client.jar") || name.endsWith("-universal.jar"))) {
                    // Extract to libraries/net/minecraftforge/forge/version/
                    String fileName = new File(name).getName();
                    
                    // Parse version from filename (e.g., "forge-1.21.8-58.0.3-client.jar")
                    if (fileName.startsWith("forge-")) {
                        String versionPart = fileName.substring(6, fileName.lastIndexOf(".jar")); // "1.21.8-58.0.3-client"
                        String[] parts = versionPart.split("-");
                        if (parts.length >= 2) {
                            // Reconstruct full version (e.g., "1.21.8-58.0.3")
                            StringBuilder fullVersion = new StringBuilder();
                            for (int i = 0; i < parts.length - 1; i++) {
                                if (i > 0) fullVersion.append("-");
                                fullVersion.append(parts[i]);
                            }
                            String classifier = parts[parts.length - 1]; // "client" or "universal"
                            
                            String libPath = "net/minecraftforge/forge/" + fullVersion + "/" + fileName;
                            File libFile = new File(ProfilePathHome.getGameHome(), "libraries/" + libPath);
                            
                            FileUtils.ensureParentDirectory(libFile);
                            
                            try (InputStream is = zipFile.getInputStream(entry);
                                 FileOutputStream fos = new FileOutputStream(libFile)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = is.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            
                            android.util.Log.d("ForgeDownloadTask", "Extracted Forge artifact: " + libPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("ForgeDownloadTask", "Failed to extract Forge libraries: " + e.getMessage());
            // Non-fatal - libraries might be downloaded later
        }
    }
    
    private byte[] readAllBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    @Override
    public void updateProgress(long curr, long max) {
        int progress100 = (int)(((float)curr / (float)max)*100f);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, progress100, R.string.mod_download_progress, mFullVersion);
    }

    private File downloadForge() throws Exception {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.mod_download_progress, mFullVersion);
        File destinationFile = new File(PathManager.DIR_CACHE, "forge-installer.jar");
        byte[] buffer = new byte[8192];
        DownloadUtils.downloadFileMonitored(mDownloadUrl, destinationFile, buffer, this);
        return destinationFile;
    }

    public boolean determineDownloadUrl() throws Exception {
        if (mDownloadUrl != null && mFullVersion != null) return true;
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.mod_forge_searching);
        if (!findVersion()) {
            throw new IOException("Version not found");
        }
        return true;
    }

    public boolean findVersion() throws Exception {
        List<String> forgeVersions = ForgeUtils.downloadForgeVersions(false);
        if(forgeVersions == null) {
            android.util.Log.e("ForgeDownloadTask", "Failed to download Forge versions list");
            return false;
        }
        
        android.util.Log.d("ForgeDownloadTask", "Looking for Forge version: " + mGameVersion + "-" + mLoaderVersion);
        android.util.Log.d("ForgeDownloadTask", "Available Forge versions: " + forgeVersions.size());
        
        String versionStart = mGameVersion + "-" + mLoaderVersion;
        for(String versionName : forgeVersions) {
            android.util.Log.d("ForgeDownloadTask", "Checking version: " + versionName + " against " + versionStart);
            if(versionName.startsWith(versionStart)) {
                mFullVersion = versionName;
                mDownloadUrl = ForgeUtils.getInstallerUrl(mFullVersion);
                android.util.Log.i("ForgeDownloadTask", "Found matching Forge version: " + mFullVersion);
                return true;
            }
        }
        
        android.util.Log.e("ForgeDownloadTask", "No matching Forge version found for " + versionStart);
        return false;
    }

}
