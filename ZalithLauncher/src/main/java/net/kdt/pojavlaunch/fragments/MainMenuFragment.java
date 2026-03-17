package net.kdt.pojavlaunch.fragments;

import static com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.END;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.movtery.anim.AnimPlayer;
import com.movtery.anim.animations.Animations;
import com.movtery.zalithlauncher.InfoCenter;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.databinding.FragmentLauncherBinding;
import com.movtery.zalithlauncher.event.single.AccountUpdateEvent;
import com.movtery.zalithlauncher.event.single.LaunchGameEvent;
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent;
import com.movtery.zalithlauncher.feature.version.Version;
import com.movtery.zalithlauncher.feature.version.utils.VersionIconUtils;
import com.movtery.zalithlauncher.feature.version.VersionInfo;
import com.movtery.zalithlauncher.feature.version.VersionsManager;
import com.movtery.zalithlauncher.feature.version.install.GameInstaller;
import com.movtery.zalithlauncher.task.TaskExecutors;
import com.movtery.zalithlauncher.ui.fragment.AboutFragment;
import com.movtery.zalithlauncher.ui.fragment.ControlButtonFragment;
import com.movtery.zalithlauncher.ui.fragment.FilesFragment;
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim;
import com.movtery.zalithlauncher.ui.fragment.VersionManagerFragment;
import com.movtery.zalithlauncher.ui.fragment.VersionsListFragment;
import com.movtery.zalithlauncher.ui.subassembly.account.AccountViewWrapper;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;

import com.kdt.mcgui.ProgressLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class MainMenuFragment extends FragmentWithAnim {
    public static final String TAG = "MainMenuFragment";
    private FragmentLauncherBinding binding;
    private AccountViewWrapper accountViewWrapper;
    private ProgressListener installProgressListener;
    private ProgressListener downloadProgressListener;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLauncherBinding.inflate(getLayoutInflater());
        accountViewWrapper = new AccountViewWrapper(this, binding.viewAccount);
        accountViewWrapper.refreshAccountInfo();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Apply grayscale filter to version card images (1900s photo style)
        android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
        matrix.setSaturation(0);
        android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(matrix);
        
        binding.vanillaImage.setColorFilter(filter);
        binding.vanillaImage.setAlpha(0.8f);
        binding.fabricImage.setColorFilter(filter);
        binding.fabricImage.setAlpha(0.8f);
        binding.customImage.setColorFilter(filter);
        binding.customImage.setAlpha(0.8f);
        binding.forgeImage.setColorFilter(filter);
        binding.forgeImage.setAlpha(0.8f);

        // Set green background for play button
        binding.playButton.setBackgroundResource(R.drawable.button_green);
        binding.playButton.setTextColor(0xFFFFFFFF);
        binding.playButton.setText("LAUNCH");
        binding.playButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24);

        // Add loader card click listeners to open version selection screen
        binding.vanillaCard.setClickable(true);
        binding.vanillaCard.setFocusable(true);
        binding.vanillaCard.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.vanillaCard, Animations.Pulse);
            Bundle bundle = new Bundle();
            bundle.putString("LOADER_TYPE", "VANILLA");
            ZHTools.swapFragmentWithAnim(this, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.class, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.TAG, bundle);
        });
        
        binding.fabricCard.setClickable(true);
        binding.fabricCard.setFocusable(true);
        binding.fabricCard.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.fabricCard, Animations.Pulse);
            Bundle bundle = new Bundle();
            bundle.putString("LOADER_TYPE", "FABRIC");
            ZHTools.swapFragmentWithAnim(this, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.class, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.TAG, bundle);
        });

        binding.customCard.setClickable(true);
        binding.customCard.setFocusable(true);
        binding.customCard.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.customCard, Animations.Pulse);
            Bundle bundle = new Bundle();
            bundle.putString("LOADER_TYPE", "CUSTOM");
            ZHTools.swapFragmentWithAnim(this, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.class, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.TAG, bundle);
        });
        
        binding.forgeCard.setClickable(true);
        binding.forgeCard.setFocusable(true);
        binding.forgeCard.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.forgeCard, Animations.Pulse);
            Bundle bundle = new Bundle();
            bundle.putString("LOADER_TYPE", "FORGE");
            ZHTools.swapFragmentWithAnim(this, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.class, com.movtery.zalithlauncher.ui.fragment.VersionSelectFragment.TAG, bundle);
        });

        binding.managerProfileButton.setOnClickListener(v -> {
            if (!isTaskRunning()) {
                ViewAnimUtils.setViewAnim(binding.managerProfileButton, Animations.Pulse);
                ZHTools.swapFragmentWithAnim(this, VersionManagerFragment.class, VersionManagerFragment.TAG, null);
            } else {
                ViewAnimUtils.setViewAnim(binding.managerProfileButton, Animations.Shake);
                TaskExecutors.runInUIThread(() -> Toast.makeText(requireContext(), R.string.version_manager_task_in_progress, Toast.LENGTH_SHORT).show());
            }
        });
        
        // Long click to open version file location
        binding.managerProfileButton.setOnLongClickListener(v -> {
            Version currentVersion = VersionsManager.INSTANCE.getCurrentVersion();
            if (currentVersion != null) {
                try {
                    java.io.File versionDir = new java.io.File(
                        com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome.getVersionsHome(),
                        currentVersion.getVersionName()
                    );
                    
                    if (versionDir.exists()) {
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                        android.net.Uri uri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:" + 
                            versionDir.getAbsolutePath().replace("/storage/emulated/0/", "").replace("/", "%2F"));
                        intent.setDataAndType(uri, "resource/folder");
                        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            // Fallback: show path in toast
                            Toast.makeText(requireContext(), "Version location: " + versionDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Error opening file location", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });

        binding.playButton.setOnClickListener(v -> {
            // Route launch-time download progress to the currently selected loader card.
            GameInstaller.currentInstallingLoader = getCurrentVersionLoaderType();
            EventBus.getDefault().post(new LaunchGameEvent());
        });

        binding.versionName.setSelected(true);
        binding.versionInfo.setSelected(true);
        
        // Add click listener for version selector
        binding.versionSelectorLayout.setOnClickListener(v -> {
            showVersionSelectorDialog();
        });

        // Setup renderer selector
        setupRendererSelector();

        refreshCurrentVersion();
        
        // Use dedicated listeners so progress is always bound to the correct card.
        installProgressListener = createProgressListener(() -> GameInstaller.currentInstallingLoader);
        downloadProgressListener = createProgressListener(this::getCurrentVersionLoaderType);
        
        // Wait for layout to be ready before setting up progress bar
        binding.vanillaCard.post(() -> {
            // Card is now laid out
        });
    }

    private interface LoaderResolver {
        @Nullable String resolveLoaderType();
    }

    private ProgressListener createProgressListener(LoaderResolver loaderResolver) {
        return new ProgressListener() {
            private String activeLoaderType = "VANILLA";

            @Override
            public void onProgressStarted() {
                TaskExecutors.runInUIThread(() -> {
                    activeLoaderType = normalizeLoaderType(loaderResolver.resolveLoaderType());
                    showProgressOnCard(activeLoaderType, 0, "Starting...");
                });
            }

            @Override
            public void onProgressUpdated(int progress, int resid, Object[] varArg) {
                TaskExecutors.runInUIThread(() -> {
                    String details = "Installing...";
                    if (resid != -1) {
                        try {
                            details = getString(resid, varArg);
                        } catch (Exception e) {
                            details = "Installing... " + progress + "%";
                        }
                    } else if (varArg != null && varArg.length > 0) {
                        details = String.valueOf(varArg[0]);
                    }
                    showProgressOnCard(activeLoaderType, progress, details);
                });
            }

            @Override
            public void onProgressEnded() {
                TaskExecutors.runInUIThread(() -> hideProgressOnCard(activeLoaderType));
            }
        };
    }

    private String getCurrentVersionLoaderType() {
        Version currentVersion = VersionsManager.INSTANCE.getCurrentVersion();
        if (currentVersion == null) return "VANILLA";

        String loaderFromName = normalizeLoaderType(currentVersion.getVersionName());
        if ("CUSTOM".equals(loaderFromName)) {
            return "CUSTOM";
        }

        VersionInfo versionInfo = currentVersion.getVersionInfo();
        if (versionInfo != null && versionInfo.getLoaderInfo() != null && versionInfo.getLoaderInfo().length > 0) {
            String loaderName = versionInfo.getLoaderInfo()[0].getName();
            return normalizeLoaderType(loaderName);
        }

        String jsonLoaderType = getLoaderTypeFromVersionJson(currentVersion);
        if (!"VANILLA".equals(jsonLoaderType)) {
            return jsonLoaderType;
        }

        return normalizeLoaderType(currentVersion.getVersionName());
    }

    private String getLoaderTypeFromVersionJson(@NonNull Version version) {
        File versionJson = new File(version.getVersionPath(), version.getVersionName() + ".json");
        if (!versionJson.exists() || !versionJson.isFile()) return "VANILLA";

        try {
            String jsonContent = new String(Files.readAllBytes(versionJson.toPath()), StandardCharsets.UTF_8)
                .toUpperCase(Locale.ROOT);
            if (jsonContent.contains("FABRIC") || jsonContent.contains("QUILT")) {
                return "FABRIC";
            }
            if (jsonContent.contains("FORGE") || jsonContent.contains("NEOFORGE")) {
                return "FORGE";
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to detect loader from version json: " + versionJson.getAbsolutePath(), e);
        }

        return "VANILLA";
    }

    private String normalizeLoaderType(@Nullable String loaderType) {
        if (loaderType == null || loaderType.isEmpty()) return "VANILLA";

        String upperLoaderType = loaderType.trim().toUpperCase(Locale.ROOT);
        if ("FABRIC".equals(upperLoaderType) || "FORGE".equals(upperLoaderType) || "VANILLA".equals(upperLoaderType) || "CUSTOM".equals(upperLoaderType)) {
            return upperLoaderType;
        }
        if (upperLoaderType.contains("DRAGON")) {
            return "CUSTOM";
        }
        if (upperLoaderType.contains("FABRIC") || upperLoaderType.contains("QUILT")) {
            return "FABRIC";
        }
        if (upperLoaderType.contains("FORGE") || upperLoaderType.contains("NEOFORGE")) {
            return "FORGE";
        }
        return "VANILLA";
    }
    
    private void showProgressOnCard(String loaderType, int progress, String details) {
        // Validate loader type
        if (loaderType == null || loaderType.isEmpty()) {
            loaderType = "VANILLA";
        }
        
        
        // Hide all progress indicators first
        binding.vanillaProgressPercent.setVisibility(View.GONE);
        binding.vanillaInstallDetails.setVisibility(View.GONE);
        binding.vanillaText.setVisibility(View.VISIBLE);
        
        binding.fabricProgressPercent.setVisibility(View.GONE);
        binding.fabricInstallDetails.setVisibility(View.GONE);
        binding.fabricText.setVisibility(View.VISIBLE);

        binding.customProgressPercent.setVisibility(View.GONE);
        binding.customInstallDetails.setVisibility(View.GONE);
        binding.customText.setVisibility(View.VISIBLE);
        
        binding.forgeProgressPercent.setVisibility(View.GONE);
        binding.forgeInstallDetails.setVisibility(View.GONE);
        binding.forgeText.setVisibility(View.VISIBLE);
        
        // Show progress on the correct card based on loader type
        if ("FABRIC".equals(loaderType)) {
            binding.fabricProgressPercent.setVisibility(View.VISIBLE);
            binding.fabricInstallDetails.setVisibility(View.VISIBLE);
            binding.fabricText.setVisibility(View.GONE);
            binding.fabricProgressPercent.setText(progress + "%");
            binding.fabricInstallDetails.setText(details);
            updateCardGrayscale(binding.fabricImage, progress);
        } else if ("CUSTOM".equals(loaderType)) {
            binding.customProgressPercent.setVisibility(View.VISIBLE);
            binding.customInstallDetails.setVisibility(View.VISIBLE);
            binding.customText.setVisibility(View.GONE);
            binding.customProgressPercent.setText(progress + "%");
            binding.customInstallDetails.setText(details);
            updateCardGrayscale(binding.customImage, progress);
        } else if ("FORGE".equals(loaderType)) {
            binding.forgeProgressPercent.setVisibility(View.VISIBLE);
            binding.forgeInstallDetails.setVisibility(View.VISIBLE);
            binding.forgeText.setVisibility(View.GONE);
            binding.forgeProgressPercent.setText(progress + "%");
            binding.forgeInstallDetails.setText(details);
            updateCardGrayscale(binding.forgeImage, progress);
        } else {
            // Default to VANILLA for any other case
            binding.vanillaProgressPercent.setVisibility(View.VISIBLE);
            binding.vanillaInstallDetails.setVisibility(View.VISIBLE);
            binding.vanillaText.setVisibility(View.GONE);
            binding.vanillaProgressPercent.setText(progress + "%");
            binding.vanillaInstallDetails.setText(details);
            updateCardGrayscale(binding.vanillaImage, progress);
        }
    }
    
    private void hideProgressOnCard(String loaderType) {
        // Validate loader type
        if (loaderType == null || loaderType.isEmpty()) {
            loaderType = "VANILLA";
        }
        
        
        if ("FABRIC".equals(loaderType)) {
            binding.fabricCard.postDelayed(() -> {
                binding.fabricProgressPercent.setVisibility(View.GONE);
                binding.fabricInstallDetails.setVisibility(View.GONE);
                binding.fabricText.setVisibility(View.VISIBLE);
                binding.fabricImage.setColorFilter(null); // Keep colorful
            }, 500);
        } else if ("CUSTOM".equals(loaderType)) {
            binding.customCard.postDelayed(() -> {
                binding.customProgressPercent.setVisibility(View.GONE);
                binding.customInstallDetails.setVisibility(View.GONE);
                binding.customText.setVisibility(View.VISIBLE);
                binding.customImage.setColorFilter(null); // Keep colorful
            }, 500);
        } else if ("FORGE".equals(loaderType)) {
            binding.forgeCard.postDelayed(() -> {
                binding.forgeProgressPercent.setVisibility(View.GONE);
                binding.forgeInstallDetails.setVisibility(View.GONE);
                binding.forgeText.setVisibility(View.VISIBLE);
                binding.forgeImage.setColorFilter(null); // Keep colorful
            }, 500);
        } else {
            // Default to VANILLA
            binding.vanillaCard.postDelayed(() -> {
                binding.vanillaProgressPercent.setVisibility(View.GONE);
                binding.vanillaInstallDetails.setVisibility(View.GONE);
                binding.vanillaText.setVisibility(View.VISIBLE);
                binding.vanillaImage.setColorFilter(null); // Keep colorful
            }, 500);
        }
    }
    
    private void updateCardGrayscale(ImageView imageView, int progress) {
        if (progress >= 0 && progress <= 100) {
            // Calculate saturation: 0% progress = 0 saturation (grayscale), 100% progress = 1 saturation (full color)
            float saturation = progress / 100f;
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(saturation);
            android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(matrix);
            imageView.setColorFilter(filter);
        }
    }

    private void refreshCurrentVersion() {
        Version version = VersionsManager.INSTANCE.getCurrentVersion();
        

        int versionInfoVisibility;
        if (version != null) {
            // Determine loader type and color
            String loaderType = "VANILLA";
            int loaderColor = 0xFF4CAF50; // Green
            int loaderIcon = R.drawable.ic_vanilla;
            
            try {
                java.io.File versionDir = new java.io.File(com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome.getVersionsHome(), version.getVersionName());
                java.io.File versionJson = new java.io.File(versionDir, version.getVersionName() + ".json");
                
                
                // Try to get loader info from VersionInfo first
                com.movtery.zalithlauncher.feature.version.VersionInfo versionInfo = version.getVersionInfo();
                String versionName = version.getVersionName() == null ? "" : version.getVersionName().toLowerCase(Locale.ROOT);
                boolean isCustomVersion = versionName.contains("dragon");
                
                if (isCustomVersion) {
                    loaderType = "DRAGON CLIENT";
                    loaderColor = 0xFF14B8A6; // Teal
                    loaderIcon = R.drawable.dragon_logo;
                } else if (versionInfo != null && versionInfo.getLoaderInfo() != null && versionInfo.getLoaderInfo().length > 0) {
                    String loaderName = versionInfo.getLoaderInfo()[0].getName().toLowerCase();
                    
                    if (loaderName.contains("fabric")) {
                        loaderType = "FABRIC";
                        loaderColor = 0xFFE91E63; // Pink
                        loaderIcon = R.drawable.ic_fabric_loader;
                    } else if (loaderName.contains("forge")) {
                        loaderType = "FORGE";
                        loaderColor = 0xFFFF9800; // Orange
                        loaderIcon = R.drawable.ic_forge;
                    }
                } else {
                    
                    // Check version name for loader type
                    if (versionName.contains("fabric")) {
                        loaderType = "FABRIC";
                        loaderColor = 0xFFE91E63; // Pink
                        loaderIcon = R.drawable.ic_fabric_loader;
                    } else if (versionName.contains("forge")) {
                        loaderType = "FORGE";
                        loaderColor = 0xFFFF9800; // Orange
                        loaderIcon = R.drawable.ic_forge;
                    } else if (versionJson.exists()) {
                        // Fallback to JSON detection if VersionInfo doesn't exist
                        String jsonContent = new String(java.nio.file.Files.readAllBytes(versionJson.toPath()));
                        
                        // Check for Fabric (case-insensitive and check for fabric-loader)
                        if (jsonContent.toLowerCase().contains("fabric")) {
                            loaderType = "FABRIC";
                            loaderColor = 0xFFE91E63; // Pink
                            loaderIcon = R.drawable.ic_fabric_loader;
                        } 
                        // Check for Forge/NeoForge (case-insensitive)
                        else if (jsonContent.toLowerCase().contains("forge")) {
                            loaderType = "FORGE";
                            loaderColor = 0xFFFF9800; // Orange
                            loaderIcon = R.drawable.ic_forge;
                        }
                    }
                }
                
            } catch (Exception e) {
                android.util.Log.e("MainMenuFragment", "Error reading version info", e);
            }
            
            // Set loader icon
            binding.versionLoaderIcon.setImageResource(loaderIcon);
            binding.versionLoaderIcon.setVisibility(View.VISIBLE);
            
            // Set version name
            binding.versionName.setText(version.getVersionName());
            
            // Set loader type as version_info with color
            binding.versionInfo.setText(loaderType);
            binding.versionInfo.setTextColor(loaderColor);
            binding.versionInfo.setVisibility(View.VISIBLE);
            versionInfoVisibility = View.VISIBLE;

            binding.managerProfileButton.setVisibility(View.VISIBLE);
        } else {
            binding.versionName.setText(R.string.version_no_versions);
            binding.versionLoaderIcon.setVisibility(View.GONE);
            binding.managerProfileButton.setVisibility(View.GONE);
            versionInfoVisibility = View.GONE;
        }
        binding.versionInfo.setVisibility(versionInfoVisibility);
        
        // Check which loaders have installed versions and keep them colorful
        checkInstalledLoaders();
    }
    
    private void checkInstalledLoaders() {
        // Check all installed versions to see which loaders are installed
        boolean hasVanilla = false;
        boolean hasFabric = false;
        boolean hasCustom = false;
        boolean hasForge = false;
        
        
        for (Version version : VersionsManager.INSTANCE.getVersions()) {
            String versionName = version.getVersionName();
            String lowerVersionName = versionName == null ? "" : versionName.toLowerCase(Locale.ROOT);

            if (lowerVersionName.contains("dragon")) {
                hasCustom = true;
                continue;
            }
            
            
            // First check VersionInfo
            try {
                com.movtery.zalithlauncher.feature.version.VersionInfo versionInfo = version.getVersionInfo();
                if (versionInfo != null && versionInfo.getLoaderInfo() != null && versionInfo.getLoaderInfo().length > 0) {
                    String loaderName = versionInfo.getLoaderInfo()[0].getName().toLowerCase();
                    
                    if (loaderName.contains("fabric")) {
                        hasFabric = true;
                        continue;
                    } else if (loaderName.contains("forge")) {
                        hasForge = true;
                        continue;
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("MainMenuFragment", "Error reading VersionInfo for " + versionName, e);
            }
            
            // Check version name
            if (lowerVersionName.contains("fabric")) {
                hasFabric = true;
                continue;
            } else if (lowerVersionName.contains("forge")) {
                hasForge = true;
                continue;
            }
            
            // Fallback: Read the version JSON file to check for loader libraries
            try {
                java.io.File versionDir = new java.io.File(com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome.getVersionsHome(), versionName);
                java.io.File versionJson = new java.io.File(versionDir, versionName + ".json");
                
                if (versionJson.exists()) {
                    String jsonContent = new String(java.nio.file.Files.readAllBytes(versionJson.toPath())).toLowerCase();
                    
                    if (jsonContent.contains("fabric")) {
                        hasFabric = true;
                    } else if (jsonContent.contains("forge")) {
                        hasForge = true;
                    } else {
                        hasVanilla = true;
                    }
                } else {
                    hasVanilla = true;
                }
            } catch (Exception e) {
                android.util.Log.e("MainMenuFragment", "Error reading version JSON for " + versionName, e);
                hasVanilla = true;
            }
        }
        
        
        // Update card colors based on what's installed
        if (hasVanilla) {
            binding.vanillaImage.setColorFilter(null);
        } else {
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0);
            binding.vanillaImage.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
        }
        
        if (hasFabric) {
            binding.fabricImage.setColorFilter(null);
        } else {
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0);
            binding.fabricImage.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
        }

        if (hasCustom) {
            binding.customImage.setColorFilter(null);
        } else {
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0);
            binding.customImage.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
        }
        
        if (hasForge) {
            binding.forgeImage.setColorFilter(null);
        } else {
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0);
            binding.forgeImage.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
        }
    }
    
    private void showVersionSelectorDialog() {
        java.util.List<Version> versions = VersionsManager.INSTANCE.getVersions();
        
        if (versions.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), R.string.version_no_versions, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create arrays for dialog
        String[] versionNames = new String[versions.size()];
        int[] versionIcons = new int[versions.size()];
        
        for (int i = 0; i < versions.size(); i++) {
            Version version = versions.get(i);
            versionNames[i] = version.getVersionName();
            
            // Determine loader icon
            try {
                java.io.File versionDir = new java.io.File(com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome.getVersionsHome(), version.getVersionName());
                java.io.File versionJson = new java.io.File(versionDir, version.getVersionName() + ".json");
                
                if (versionJson.exists()) {
                    String jsonContent = new String(java.nio.file.Files.readAllBytes(versionJson.toPath()));
                    
                    if (jsonContent.contains("fabric") || jsonContent.contains("Fabric")) {
                        versionIcons[i] = R.drawable.ic_fabric_loader;
                    } else if (jsonContent.contains("forge") || jsonContent.contains("Forge") || jsonContent.contains("neoforge") || jsonContent.contains("NeoForge")) {
                        versionIcons[i] = R.drawable.ic_forge;
                    } else {
                        versionIcons[i] = R.drawable.ic_vanilla;
                    }
                } else {
                    versionIcons[i] = R.drawable.ic_vanilla;
                }
            } catch (Exception e) {
                versionIcons[i] = R.drawable.ic_vanilla;
            }
        }
        
        // Determine loader types for each version
        String[] loaderTypes = new String[versions.size()];
        int[] loaderColors = new int[versions.size()];
        
        for (int i = 0; i < versions.size(); i++) {
            Version version = versions.get(i);
            
            // Default values
            versionIcons[i] = R.drawable.ic_vanilla;
            loaderTypes[i] = "VANILLA";
            loaderColors[i] = 0xFF4CAF50; // Green

            String lowerVersionName = version.getVersionName() == null ? "" : version.getVersionName().toLowerCase(Locale.ROOT);
            if (lowerVersionName.contains("dragon")) {
                versionIcons[i] = R.drawable.dragon_logo;
                loaderTypes[i] = "DRAGON CLIENT";
                loaderColors[i] = 0xFF14B8A6; // Teal
                continue;
            }
            
            // Try to get loader info from VersionInfo first
            try {
                com.movtery.zalithlauncher.feature.version.VersionInfo versionInfo = version.getVersionInfo();
                if (versionInfo != null && versionInfo.getLoaderInfo() != null && versionInfo.getLoaderInfo().length > 0) {
                    String loaderName = versionInfo.getLoaderInfo()[0].getName().toLowerCase();
                    
                    if (loaderName.contains("fabric")) {
                        versionIcons[i] = R.drawable.ic_fabric_loader;
                        loaderTypes[i] = "FABRIC";
                        loaderColors[i] = 0xFFE91E63; // Pink
                    } else if (loaderName.contains("forge")) {
                        versionIcons[i] = R.drawable.ic_forge;
                        loaderTypes[i] = "FORGE";
                        loaderColors[i] = 0xFFFF9800; // Orange
                    } else if (loaderName.contains("quilt")) {
                        versionIcons[i] = R.drawable.ic_fabric_loader;
                        loaderTypes[i] = "QUILT";
                        loaderColors[i] = 0xFF9C27B0; // Purple
                    }
                } else {
                    // Fallback to JSON detection
                    java.io.File versionDir = new java.io.File(com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome.getVersionsHome(), version.getVersionName());
                    java.io.File versionJson = new java.io.File(versionDir, version.getVersionName() + ".json");
                    
                    if (versionJson.exists()) {
                        String jsonContent = new String(java.nio.file.Files.readAllBytes(versionJson.toPath())).toLowerCase();
                        
                        if (jsonContent.contains("fabric")) {
                            versionIcons[i] = R.drawable.ic_fabric_loader;
                            loaderTypes[i] = "FABRIC";
                            loaderColors[i] = 0xFFE91E63; // Pink
                        } else if (jsonContent.contains("forge")) {
                            versionIcons[i] = R.drawable.ic_forge;
                            loaderTypes[i] = "FORGE";
                            loaderColors[i] = 0xFFFF9800; // Orange
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("MainMenuFragment", "Error detecting loader type for " + version.getVersionName(), e);
            }
        }
        
        // Create custom adapter for dialog with icons and smaller size
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(requireContext(), R.layout.item_version_selector, R.id.version_name, versionNames) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = convertView;
                if (view == null) {
                    view = android.view.LayoutInflater.from(getContext()).inflate(R.layout.item_version_selector, parent, false);
                }
                
                android.widget.ImageView iconView = view.findViewById(R.id.version_icon);
                android.widget.TextView loaderTypeView = view.findViewById(R.id.loader_type);
                android.widget.TextView textView = view.findViewById(R.id.version_name);
                
                iconView.setImageResource(versionIcons[position]);
                loaderTypeView.setText(loaderTypes[position]);
                loaderTypeView.setTextColor(loaderColors[position]);
                
                // Remove loader type from version name display
                String displayName = versionNames[position];
                displayName = displayName.replace(" Fabric", "").replace(" Forge", "").replace(" fabric", "").replace(" forge", "");
                textView.setText(displayName);
                
                return view;
            }
        };
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Version")
            .setAdapter(adapter, (dialogInterface, which) -> {
                Version selectedVersion = versions.get(which);
                String versionName = selectedVersion.getVersionName();
                VersionsManager.INSTANCE.saveCurrentVersion(versionName);
                String currentVer = VersionsManager.INSTANCE.getCurrentVersion() != null ? VersionsManager.INSTANCE.getCurrentVersion().getVersionName() : "null";
                refreshCurrentVersion();
            })
            .create();
        
        // Position dialog on the right side with max height
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
            params.width = (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.28);
            // Set max height to 60% of screen height to make it scrollable
            params.height = (int) (requireContext().getResources().getDisplayMetrics().heightPixels * 0.6);
            params.x = 50; // Small margin from right edge
            window.setAttributes(params);
            
            // Find the ListView and make it scrollable
            android.widget.ListView listView = dialog.getListView();
            if (listView != null) {
                listView.setDivider(null);
                listView.setDividerHeight(0);
            }
        }
    }

    @Subscribe()
    public void event(RefreshVersionsEvent event) {
        if (event.getMode() == END) {
            TaskExecutors.runInUIThread(this::refreshCurrentVersion);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void event(AccountUpdateEvent event) {
        if (accountViewWrapper != null) accountViewWrapper.refreshAccountInfo();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Auto-refresh when returning to this fragment
        refreshCurrentVersion();
        if (accountViewWrapper != null) accountViewWrapper.refreshAccountInfo();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        // Use dedicated listeners so launch downloads and installer steps route to the correct loader card.
        ProgressKeeper.addListener(ProgressLayout.DOWNLOAD_MINECRAFT, downloadProgressListener);
        ProgressKeeper.addListener(ProgressLayout.INSTALL_RESOURCE, installProgressListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        ProgressKeeper.removeListener(ProgressLayout.DOWNLOAD_MINECRAFT, downloadProgressListener);
        ProgressKeeper.removeListener(ProgressLayout.INSTALL_RESOURCE, installProgressListener);
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }

    private void setupRendererSelector() {
        try {
            // Initialize renderers if not already done
            com.movtery.zalithlauncher.renderer.Renderers.INSTANCE.init(false);
            
            // Get compatible renderers for this device.
            kotlin.Pair<com.movtery.zalithlauncher.renderer.RenderersList, java.util.List<com.movtery.zalithlauncher.renderer.RendererInterface>> rendererPair =
                com.movtery.zalithlauncher.renderer.Renderers.INSTANCE.getCompatibleRenderers(requireContext());
            java.util.List<com.movtery.zalithlauncher.renderer.RendererInterface> deviceCompatibleRenderers = rendererPair.getSecond();

            // Further filter by the currently selected Minecraft version to avoid renderer-related crashes.
            Version currentVersion = VersionsManager.INSTANCE.getCurrentVersion();
            String selectedMcVersion = null;
            if (currentVersion != null) {
                VersionInfo versionInfo = currentVersion.getVersionInfo();
                if (versionInfo != null && versionInfo.getMinecraftVersion() != null && !versionInfo.getMinecraftVersion().isEmpty()) {
                    selectedMcVersion = versionInfo.getMinecraftVersion();
                } else {
                    selectedMcVersion = currentVersion.getVersionName();
                }
            }

            java.util.List<String> rendererNames = new java.util.ArrayList<>();
            java.util.List<String> rendererIds = new java.util.ArrayList<>();
            for (com.movtery.zalithlauncher.renderer.RendererInterface renderer : deviceCompatibleRenderers) {
                if (selectedMcVersion == null || renderer.supportsVersion(selectedMcVersion)) {
                    rendererNames.add(renderer.getRendererName());
                    rendererIds.add(renderer.getUniqueIdentifier());
                }
            }

            // Fallback to device-compatible list when current version info is unavailable.
            if (rendererIds.isEmpty()) {
                for (com.movtery.zalithlauncher.renderer.RendererInterface renderer : deviceCompatibleRenderers) {
                    rendererNames.add(renderer.getRendererName());
                    rendererIds.add(renderer.getUniqueIdentifier());
                }
            }
            if (rendererIds.isEmpty()) {
                android.util.Log.w(TAG, "No compatible renderer found for current device/version.");
                return;
            }
            
            // Get current renderer
            String currentRendererId = "";
            if (com.movtery.zalithlauncher.renderer.Renderers.INSTANCE.isCurrentRendererValid()) {
                currentRendererId = com.movtery.zalithlauncher.renderer.Renderers.INSTANCE.getCurrentRenderer().getUniqueIdentifier();
            }
            
            // Create adapter with plain black text.
            final String finalCurrentRendererId = currentRendererId;
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                requireContext(),
                R.layout.item_renderer_spinner,
                R.id.renderer_name,
                rendererNames
            ) {
                @Override
                public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                    android.view.View view = super.getView(position, convertView, parent);
                    android.widget.TextView textView = view.findViewById(R.id.renderer_name);
                    textView.getPaint().setShader(null);
                    textView.setTextColor(0xFF000000);
                    
                    return view;
                }
                
                @Override
                public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                    android.view.View view = super.getDropDownView(position, convertView, parent);
                    android.widget.TextView textView = view.findViewById(R.id.renderer_name);
                    textView.getPaint().setShader(null);
                    textView.setTextColor(0xFF000000);
                    
                    return view;
                }
            };
            
            binding.rendererSelector.setAdapter(adapter);
            
            // Set current selection
            for (int i = 0; i < rendererIds.size(); i++) {
                if (rendererIds.get(i).equals(finalCurrentRendererId)) {
                    binding.rendererSelector.setSelection(i);
                    break;
                }
            }
            
            // Set listener
            binding.rendererSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    if (position < 0 || position >= rendererIds.size()) return;
                    String selectedRendererId = rendererIds.get(position);
                    com.movtery.zalithlauncher.renderer.Renderers.INSTANCE.setCurrentRenderer(requireContext(), selectedRendererId, true);
                    com.movtery.zalithlauncher.setting.AllSettings.getRenderer().put(selectedRendererId).save();
                }
                
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    // Do nothing
                }
            });
        } catch (Exception e) {
            android.util.Log.e("MainMenuFragment", "Error setting up renderer selector", e);
        }
    }

    @Override
    public void slideIn(AnimPlayer animPlayer) {
        animPlayer.apply(new AnimPlayer.Entry(binding.launcherMenu, Animations.FadeIn))
                .apply(new AnimPlayer.Entry(binding.playLayout, Animations.FadeIn));
    }

    @Override
    public void slideOut(AnimPlayer animPlayer) {
        animPlayer.apply(new AnimPlayer.Entry(binding.launcherMenu, Animations.FadeOut))
                .apply(new AnimPlayer.Entry(binding.playLayout, Animations.FadeOut));
    }
}
