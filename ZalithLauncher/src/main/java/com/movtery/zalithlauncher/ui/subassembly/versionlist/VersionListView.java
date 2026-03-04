package com.movtery.zalithlauncher.ui.subassembly.versionlist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.event.sticky.MinecraftVersionValueEvent;
import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.task.TaskExecutors;
import com.movtery.zalithlauncher.ui.subassembly.filelist.FileItemBean;
import com.movtery.zalithlauncher.ui.subassembly.filelist.FileRecyclerViewCreator;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FilteredSubList;

import org.greenrobot.eventbus.EventBus;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import kotlin.Pair;

public class VersionListView extends LinearLayout {
    private Context context;
    private List<JMinecraftVersionList.Version> releaseList, snapshotList, betaList, alphaList;
    private FileRecyclerViewCreator fileRecyclerViewCreator;
    private VersionSelectedListener versionSelectedListener;
    private int loaderIconRes = R.drawable.ic_minecraft; // Default to vanilla
    private String versionFilter = null; // Filter for specific version (e.g., "1.21")
    private android.widget.TextView emptyStateTextView;

    public VersionListView(Context context) {
        this(context, null);
    }

    public VersionListView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VersionListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private void init(Context context) {
        this.context = context;

        LayoutParams layParam = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        setOrientation(VERTICAL);

        RecyclerView mainListView = new RecyclerView(context);
        
        // Create empty state text view
        emptyStateTextView = new android.widget.TextView(context);
        emptyStateTextView.setText("No versions available");
        emptyStateTextView.setTextSize(16);
        emptyStateTextView.setGravity(android.view.Gravity.CENTER);
        emptyStateTextView.setPadding(32, 32, 32, 32);
        emptyStateTextView.setVisibility(GONE);

        JMinecraftVersionList.Version[] versionArray;
        MinecraftVersionValueEvent event = EventBus.getDefault().getStickyEvent(MinecraftVersionValueEvent.class);

        if (event != null) {
            JMinecraftVersionList jMinecraftVersionList = event.getList();
            boolean isVersionsNotNull = jMinecraftVersionList != null && jMinecraftVersionList.versions != null;
            versionArray = isVersionsNotNull ? jMinecraftVersionList.versions : new JMinecraftVersionList.Version[0];
        } else {
            versionArray = new JMinecraftVersionList.Version[0];
        }

        releaseList = new FilteredSubList<>(versionArray, item -> item.type.equals("release"));
        snapshotList = new FilteredSubList<>(versionArray, item -> item.type.equals("snapshot"));
        betaList = new FilteredSubList<>(versionArray, item -> item.type.equals("old_beta"));
        alphaList = new FilteredSubList<>(versionArray, item -> item.type.equals("old_alpha"));

        fileRecyclerViewCreator = new FileRecyclerViewCreator(
                context,
                mainListView,
                (position, fileItemBean) -> versionSelectedListener.onVersionSelected(fileItemBean.name),
                null,
                showVersions(VersionType.RELEASE)
        );

        addView(mainListView, layParam);
        addView(emptyStateTextView, layParam);
    }

    private Pair<String, Date>[] getVersionPair(List<JMinecraftVersionList.Version> versions) {
        List<Pair<String, Date>> pairList = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            JMinecraftVersionList.Version version = versions.get(i);
            
            // Apply version filter if set (e.g., only show 1.21.x versions)
            if (versionFilter != null && !version.id.startsWith(versionFilter)) {
                continue;
            }
            
            Date date;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(version.releaseTime, formatter);
                date = Date.from(zonedDateTime.toInstant());
            } catch (Exception e) {
                Logging.e("Version List", Tools.printToString(e));
                date = null;
            }
            pairList.add(new Pair<>(version.id, date));
        }
        return pairList.toArray(new Pair[0]);
    }

    public void setVersionSelectedListener(VersionSelectedListener versionSelectedListener) {
        this.versionSelectedListener = versionSelectedListener;
    }
    
    public void setLoaderIcon(int iconRes) {
        this.loaderIconRes = iconRes;
        // Refresh the current view with the new icon
        if (fileRecyclerViewCreator != null) {
            setVersionType(VersionType.RELEASE);
        }
    }
    
    public void setVersionFilter(String versionPrefix) {
        this.versionFilter = versionPrefix;
        // Refresh the current view with the filter
        if (fileRecyclerViewCreator != null) {
            setVersionType(VersionType.RELEASE);
        }
    }

    public void setVersionType(VersionType versionType) {
        showVersions(versionType);
    }

    public void setFilterString(String filterString) {
        this.fileRecyclerViewCreator.setFilterString(filterString);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private List<FileItemBean> showVersions(VersionType versionType) {
        switch (versionType) {
            case SNAPSHOT:
                return getVersion(context.getDrawable(R.drawable.ic_command_block), getVersionPair(snapshotList));
            case BETA:
                return getVersion(context.getDrawable(R.drawable.ic_old_cobblestone), getVersionPair(betaList));
            case ALPHA:
                return getVersion(context.getDrawable(R.drawable.ic_old_grass_block), getVersionPair(alphaList));
            case RELEASE:
            default:
                // Use the loader icon for release versions
                return getVersion(context.getDrawable(loaderIconRes), getVersionPair(releaseList));
        }
    }

    private List<FileItemBean> getVersion(Drawable icon, Pair<String, Date>[] namesPair) {
        List<FileItemBean> itemBeans = FileRecyclerViewCreator.loadItemBean(icon, namesPair);
        
        // Show/hide empty state based on whether there are items
        if (emptyStateTextView != null) {
            if (itemBeans.isEmpty()) {
                emptyStateTextView.setVisibility(VISIBLE);
                String message = "No versions available";
                if (versionFilter != null) {
                    message = "No " + versionFilter + " versions available";
                }
                emptyStateTextView.setText(message);
            } else {
                emptyStateTextView.setVisibility(GONE);
            }
        }
        
        TaskExecutors.runInUIThread(() -> fileRecyclerViewCreator.loadData(itemBeans));
        return itemBeans;
    }
}
