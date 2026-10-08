package com.example.gtj_f230_rebound_corrosion.base.activity.data;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.blackhao.utillibrary.usbHelper.USBBroadCastReceiver;
import com.blackhao.utillibrary.usbHelper.UsbHelper;
import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.SizeUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.example.gtj_f230_rebound_corrosion.R;
import com.example.gtj_f230_rebound_corrosion.StaticConstant;
import com.example.gtj_f230_rebound_corrosion.base.activity.BaseActivity;
import com.example.gtj_f230_rebound_corrosion.base.adapter.DataProjectListAdapter;
import com.example.gtj_f230_rebound_corrosion.base.adapter.ZoneListAdapter;
import com.example.gtj_f230_rebound_corrosion.base.model.ProjectBean;
import com.example.gtj_f230_rebound_corrosion.base.model.ZoneBean;
import com.example.gtj_f230_rebound_corrosion.base.storage.GreenDaoHelper;
import com.example.gtj_f230_rebound_corrosion.base.utils.DeleteFileUtils;
import com.example.gtj_f230_rebound_corrosion.base.utils.OtherUtils;
import com.example.gtj_f230_rebound_corrosion.base.utils.RecycleBinUtils;
import com.example.gtj_f230_rebound_corrosion.base.view.StyleAlertDialog;
import com.example.gtj_f230_rebound_corrosion.databinding.ActDataManagerBinding;
import com.example.gtj_f230_rebound_corrosion.functions.database.greenDao.db.ZoneBeanDao;
import com.example.gtj_f230_rebound_corrosion.thickness.model.Rebar240ZoneBean;
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.google.gson.Gson;
import com.orhanobut.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 数据管理
 */
@SuppressLint("SetTextI18n")
public class DataManagerActivity extends BaseActivity<ActDataManagerBinding> {
    private DataProjectListAdapter projectListAdapter;
    private ProjectBean projectBean;
    private ZoneListAdapter zoneListAdapter;
    private ZoneBean zoneBean;
    private final Set<Long> treatZoneIdSet = new HashSet<>();

    //当前显示的对话框，避免页面销毁后 WindowLeaked / BadTokenException
    private StyleAlertDialog currentDialog;
    //回收站下拉框
    private ListPopupWindow recycleBinWindow;

    @Override
    protected ActDataManagerBinding getBinding() {
        return ActDataManagerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initLabel() {
        initBatteryReceiver(binding.navigationBar);
        initDeviceStatusExecutor(binding.navigationBar);

        binding.navigationBar.setTitleText("数据管理");
    }

    @Override
    protected void initView() {
        initUsbHelper();
        setProjectRecyclerView();
        setZoneRecyclerView();
        queryPageListProject();

        binding.btnOpen.setOnClickListener(this::onViewClicked);
        binding.btnUpload.setOnClickListener(this::onViewClicked);
        binding.btnExport.setOnClickListener(this::onViewClicked);
        binding.btnDelete.setOnClickListener(this::onViewClicked);
        binding.btnRecycle.setOnClickListener(this::onViewClicked);
        binding.btnReturn.setOnClickListener(this::onViewClicked);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setProjectRecyclerView() {
        binding.recyclerViewProject.setLayoutManager(new LinearLayoutManager(this));
        projectListAdapter = new DataProjectListAdapter(this);
        binding.recyclerViewProject.setAdapter(projectListAdapter);
        projectListAdapter.addOnItemChildClickListener(R.id.textView, (adapter, view, position) -> {
            List<ProjectBean> items = projectListAdapter.getItems();
            if (position < 0 || position >= items.size()) {
                return;
            }
            ProjectBean project = items.get(position);
            if (project == null) {
                return;
            }
            for (ProjectBean bean : items) {
                if (bean != null) {
                    bean.isSelected_open = false;
                }
            }
            project.isSelected_open = true;
            projectListAdapter.notifyDataSetChanged();
            projectBean = project;
            //清空detail
            zoneBean = null;
            refreshTextView(false);
            //搜索zoneList
            queryShowZoneList();
        });
        projectListAdapter.addOnItemChildClickListener(R.id.imageView, (adapter, view, position) -> {
            List<ProjectBean> items = projectListAdapter.getItems();
            if (position < 0 || position >= items.size()) {
                return;
            }
            ProjectBean project = items.get(position);
            if (project == null || project.id == null) {
                return;
            }
            boolean flag = !project.isSelected_treat;
            project.isSelected_treat = flag;
            projectListAdapter.notifyDataSetChanged();
            queryTreatZoneList(project);
            List<ZoneBean> zoneItems = zoneListAdapter.getItems();
            if (!zoneItems.isEmpty() && zoneItems.get(0) != null && zoneItems.get(0).projectId == project.id) {
                for (ZoneBean bean : zoneItems) {
                    if (bean != null) {
                        bean.isSelected_treat = flag;
                    }
                }
                zoneListAdapter.notifyDataSetChanged();
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setZoneRecyclerView() {
        binding.recyclerViewZone.setLayoutManager(new LinearLayoutManager(this));
        zoneListAdapter = new ZoneListAdapter(this);
        binding.recyclerViewZone.setAdapter(zoneListAdapter);
        zoneListAdapter.addOnItemChildClickListener(R.id.textView, (adapter, view, position) -> {
            List<ZoneBean> items = zoneListAdapter.getItems();
            if (position < 0 || position >= items.size()) {
                return;
            }
            ZoneBean zone = items.get(position);
            if (zone == null) {
                return;
            }
            for (ZoneBean bean : items) {
                if (bean != null) {
                    bean.isSelected_open = false;
                }
            }
            zone.isSelected_open = true;
            zoneListAdapter.notifyDataSetChanged();
            zoneBean = zone;
            //显示detail
            refreshTextView(true);
        });
        zoneListAdapter.addOnItemChildClickListener(R.id.imageView, (adapter, view, position) -> {
            List<ZoneBean> items = zoneListAdapter.getItems();
            if (position < 0 || position >= items.size()) {
                return;
            }
            ZoneBean zone = items.get(position);
            if (zone == null || zone.id == null) {
                return;
            }
            boolean flag = !zone.isSelected_treat;
            zone.isSelected_treat = flag;
            zoneListAdapter.notifyDataSetChanged();
            if (flag) {
                treatZoneIdSet.add(zone.id);
            } else {
                treatZoneIdSet.remove(zone.id);
            }
            //按 id 定位所属工程，避免使用可能越界的下标
            ProjectBean parent = findProjectById(zone.projectId);
            if (parent != null) {
                boolean isUnSelected = items.isEmpty();
                for (ZoneBean bean : items) {
                    if (bean != null && !bean.isSelected_treat) {
                        isUnSelected = true;
                        break;
                    }
                }
                parent.isSelected_treat = !isUnSelected;
                projectListAdapter.notifyDataSetChanged();
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    public void queryPageListProject() {
        Set<Long> recycledProjectIds = RecycleBinUtils.getRecycledProjectIds();
        List<ProjectBean> showList = new ArrayList<>();
        List<ProjectBean> projectBeanList = GreenDaoHelper.getDaoSession(this).getProjectBeanDao().loadAll();
        if (projectBeanList != null) {
            for (ProjectBean bean : projectBeanList) {
                if (bean == null || bean.id == null) {
                    continue;
                }
                //回收站中的工程不显示
                if (recycledProjectIds.contains(bean.id)) {
                    continue;
                }
                bean.isSelected_open = false;
                bean.isSelected_treat = false;
                showList.add(bean);
            }
        }
        projectListAdapter.submitList(showList);
        //当前选中的工程可能已进回收站或被彻底删除，必须重置，避免后续使用失效对象
        ProjectBean selected = findProjectById(projectBean == null ? null : projectBean.id);
        if (selected == null) {
            projectBean = null;
            zoneBean = null;
            zoneListAdapter.submitList(new ArrayList<>());
            refreshTextView(false);
        } else {
            projectBean = selected;
            selected.isSelected_open = true;
            projectListAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void queryShowZoneList() {
        if (projectBean == null || projectBean.id == null) {
            zoneListAdapter.submitList(new ArrayList<>());
            zoneBean = null;
            refreshTextView(false);
            return;
        }
        Set<Long> recycledZoneIds = RecycleBinUtils.getRecycledZoneIds();
        List<ZoneBean> showList = new ArrayList<>();
        List<ZoneBean> zoneBeanList = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().queryBuilder()
                .where(ZoneBeanDao.Properties.ProjectId.eq(projectBean.id)).list();
        if (zoneBeanList != null) {
            for (ZoneBean bean : zoneBeanList) {
                if (bean == null || bean.id == null) {
                    continue;
                }
                //回收站中的构件不显示
                if (recycledZoneIds.contains(bean.id)) {
                    continue;
                }
                bean.isSelected_open = false;
                bean.isSelected_treat = treatZoneIdSet.contains(bean.id);
                showList.add(bean);
            }
        }
        zoneListAdapter.submitList(showList);
        ZoneBean selected = findZoneById(zoneBean == null ? null : zoneBean.id);
        if (selected == null) {
            zoneBean = null;
            refreshTextView(false);
        } else {
            zoneBean = selected;
            selected.isSelected_open = true;
            zoneListAdapter.notifyDataSetChanged();
            refreshTextView(true);
        }
    }

    private void refreshTextView(boolean flagShow) {
        if (flagShow && zoneBean != null) {
            binding.tvDetail.setText(getStringBuilder());
        } else {
            binding.tvDetail.setText("");
        }
    }

    @NonNull
    private StringBuilder getStringBuilder() {
        StringBuilder sbd = new StringBuilder();
        sbd.append("工程名称：").append(safeText(projectBean == null ? null : projectBean.name))
                .append("\n工程地址：").append(safeText(projectBean == null ? null : projectBean.address))
                .append("\n检测时间：").append(getZoneTime(zoneBean))
                .append("\n构件编号：").append(safeText(zoneBean == null ? null : zoneBean.number))
                .append("\n构件备注：").append(safeText(zoneBean == null ? null : zoneBean.remark))
                .append("\n缝宽测点：").append(zoneBean == null || zoneBean.f230ZoneBean == null ? 0 : zoneBean.f230ZoneBean.zoneCount)
                .append("\n回弹测点：").append(zoneBean == null || zoneBean.rebounderZoneBean == null ? 0 : zoneBean.rebounderZoneBean.zoneCount);
        if (TextUtils.equals(StaticConstant.APP_TYPE_DEFAULT, StaticConstant.APP_CORROSION)) {
            sbd.append("\n锈蚀测点：").append(zoneBean == null || zoneBean.corrosionZoneBean == null ? 0 : zoneBean.corrosionZoneBean.zoneCount);
        } else {
            sbd.append("\n保护层厚度测点：").append(zoneBean == null ? 0 : zoneBean.thicknessCount);
        }
        return sbd;
    }

    /**
     * 取该构件最早的检测时间，时间格式：yyyy-MM-dd HH:mm:ss（可直接按字符串比较大小）
     */
    private String getZoneTime(ZoneBean bean) {
        if (bean == null) {
            return "未记录";
        }
        String time = "";
        if (bean.f230ZoneBean != null) {
            time = earlierTime(time, bean.f230ZoneBean.startTime);
        }
        if (bean.rebounderZoneBean != null) {
            time = earlierTime(time, bean.rebounderZoneBean.startTime);
        }
        if (bean.corrosionZoneBean != null) {
            time = earlierTime(time, bean.corrosionZoneBean.startTime);
        }
        if (TextUtils.isEmpty(time) && bean.thicknessId > 0) {
            try {
                Rebar240ZoneBean rebarZoneBean = GreenDaoHelper.getDaoSession(this).getRebar240ZoneBeanDao().load(bean.thicknessId);
                if (rebarZoneBean != null) {
                    time = earlierTime(time, rebarZoneBean.startTime);
                }
            } catch (Exception e) {
                Logger.e("读取保护层厚度检测时间失败", e);
            }
        }
        return TextUtils.isEmpty(time) ? "未记录" : time;
    }

    private String earlierTime(String time1, String time2) {
        if (TextUtils.isEmpty(time1)) {
            return TextUtils.isEmpty(time2) ? "" : time2;
        }
        if (TextUtils.isEmpty(time2)) {
            return time1;
        }
        return time1.compareTo(time2) <= 0 ? time1 : time2;
    }

    private String safeText(String text) {
        return TextUtils.isEmpty(text) ? "" : text;
    }

    public void queryTreatZoneList(ProjectBean projectBean) {
        if (projectBean == null || projectBean.id == null) {
            return;
        }
        List<ZoneBean> zoneBeanList = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().queryBuilder()
                .where(ZoneBeanDao.Properties.ProjectId.eq(projectBean.id)).list();
        if (zoneBeanList == null) {
            return;
        }
        Set<Long> recycledZoneIds = RecycleBinUtils.getRecycledZoneIds();
        boolean flag = projectBean.isSelected_treat;
        for (ZoneBean bean : zoneBeanList) {
            if (bean == null || bean.id == null || recycledZoneIds.contains(bean.id)) {
                continue;
            }
            if (flag) {
                treatZoneIdSet.add(bean.id);
            } else {
                treatZoneIdSet.remove(bean.id);
            }
        }
    }

    private ProjectBean findProjectById(Long projectId) {
        if (projectId == null || projectListAdapter == null) {
            return null;
        }
        for (ProjectBean bean : projectListAdapter.getItems()) {
            if (bean != null && projectId.equals(bean.id)) {
                return bean;
            }
        }
        return null;
    }

    private ZoneBean findZoneById(Long zoneId) {
        if (zoneId == null || zoneListAdapter == null) {
            return null;
        }
        for (ZoneBean bean : zoneListAdapter.getItems()) {
            if (bean != null && zoneId.equals(bean.id)) {
                return bean;
            }
        }
        return null;
    }

    public void onViewClicked(View view) {
        OtherUtils.hideKeyboard(this);
        int viewId = view.getId();
        if (viewId == R.id.btn_open) {
            openZoneDetails();
        } else if (viewId == R.id.btn_upload) {
            ToastUtils.showShort("暂无上传功能");
        } else if (viewId == R.id.btn_export) {
            exportData();
        } else if (viewId == R.id.btn_delete) {
            deleteCheckedItems();
        } else if (viewId == R.id.btn_recycle) {
            showRecycleBinWindow();
        } else if (viewId == R.id.btn_return) {
            dismissRecycleBinWindow();
            dismissCurrentDialog();
            finish();
        }
    }

    //---------------------打开--------------------------
    private void openZoneDetails() {
        if (projectBean == null || projectBean.id == null) {
            ToastUtils.showShort("请选择一个工程");
            return;
        }
        if (zoneBean == null || zoneBean.id == null) {
            ToastUtils.showShort("请选择一个构件");
            return;
        }
        //以数据库最新数据为准，避免使用已删除/过期的对象导致详情页空指针
        ZoneBean latestZone = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().load(zoneBean.id);
        if (latestZone == null) {
            ToastUtils.showShort("该构件数据不存在，请重新选择");
            zoneBean = null;
            refreshTextView(false);
            return;
        }
        ProjectBean latestProject = GreenDaoHelper.getDaoSession(this).getProjectBeanDao().load(latestZone.projectId);
        if (latestProject == null) {
            ToastUtils.showShort("该构件所属工程不存在，请重新选择");
            projectBean = null;
            zoneBean = null;
            queryPageListProject();
            return;
        }
        projectBean = latestProject;
        zoneBean = latestZone;
        try {
            Intent intent = new Intent(this, DataDetailsActivity.class);
            //只传主键，详情页自行查库，避免 Serializable 过大触发 TransactionTooLargeException
            intent.putExtra(DataDetailsActivity.EXTRA_PROJECT_ID, latestProject.id.longValue());
            intent.putExtra(DataDetailsActivity.EXTRA_ZONE_ID, latestZone.id.longValue());
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("打开检测数据失败", e);
            ToastUtils.showShort("打开失败，请重试");
        }
    }

    //---------------------导出--------------------------
    private void exportData() {
        if (treatZoneIdSet.isEmpty()) {
            ToastUtils.showShort("暂无可导出的数据");
            return;
        }
        //U盘校验失败必须中断流程，否则会在无U盘时继续进入导出页
        if (StaticConstant.usbHelper == null || StaticConstant.usbHelper.rootFolder == null) {
            ToastUtils.showShort("未识别到U盘");
            return;
        }
        //剔除数据库中已不存在的构件，避免导出页 unique() 取到 null 后崩溃
        Set<Long> validIdSet = new HashSet<>();
        for (Long id : treatZoneIdSet) {
            if (id == null) {
                continue;
            }
            if (GreenDaoHelper.getDaoSession(this).getZoneBeanDao().load(id) != null) {
                validIdSet.add(id);
            }
        }
        treatZoneIdSet.retainAll(validIdSet);
        if (treatZoneIdSet.isEmpty()) {
            ToastUtils.showShort("暂无可导出的数据");
            queryShowZoneList();
            return;
        }
        showExportDialog();
    }

    private void showExportDialog() {
        StyleAlertDialog styleAlertDialog = new StyleAlertDialog(this);
        styleAlertDialog.setContent("确定导出数据？")
                .setLeftButton("取消", v -> styleAlertDialog.dismiss())
                .setRightButton("确定", v -> {
                    styleAlertDialog.dismiss();
                    startExportActivity();
                });
        showDialogSafely(styleAlertDialog);
    }

    private void startExportActivity() {
        //点击“确定”时U盘可能已被拔出，需再次校验
        if (StaticConstant.usbHelper == null || StaticConstant.usbHelper.rootFolder == null) {
            ToastUtils.showShort("未识别到U盘");
            return;
        }
        if (treatZoneIdSet.isEmpty()) {
            ToastUtils.showShort("暂无可导出的数据");
            return;
        }
        try {
            Intent intent;
            if (TextUtils.equals(StaticConstant.APP_TYPE_DEFAULT, StaticConstant.APP_CORROSION)) {
                intent = new Intent(this, DataExportDataActivity.class);
            } else {
                intent = new Intent(this, DataExportThicknessActivity.class);
            }
            intent.putExtra("treatZoneIdSet", new Gson().toJson(treatZoneIdSet));
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("跳转数据导出页失败", e);
            ToastUtils.showShort("导出失败，请重试");
        }
    }

    //---------------------删除（移入回收站）--------------------------
    private void deleteCheckedItems() {
        Set<Long> checkedProjectIds = new HashSet<>();
        for (ProjectBean bean : projectListAdapter.getItems()) {
            if (bean != null && bean.id != null && bean.isSelected_treat) {
                checkedProjectIds.add(bean.id);
            }
        }
        if (checkedProjectIds.isEmpty() && treatZoneIdSet.isEmpty()) {
            ToastUtils.showShort("请勾选删除项");
            return;
        }
        StyleAlertDialog styleAlertDialog = new StyleAlertDialog(this);
        styleAlertDialog.setContent("确定删除勾选项？\n删除后移入回收站，可在回收站中恢复。")
                .setLeftButton("取消", v -> styleAlertDialog.dismiss())
                .setRightButton("确定", v -> {
                    styleAlertDialog.dismiss();
                    moveToRecycleBin(checkedProjectIds);
                });
        showDialogSafely(styleAlertDialog);
    }

    private void moveToRecycleBin(Set<Long> checkedProjectIds) {
        RecycleBinUtils.addProjects(checkedProjectIds);
        RecycleBinUtils.addZones(treatZoneIdSet);
        treatZoneIdSet.clear();
        queryPageListProject();
        queryShowZoneList();
        ToastUtils.showShort("已移入回收站");
    }

    //---------------------回收站--------------------------
    private void showRecycleBinWindow() {
        List<RecycleItem> items = loadRecycleItems();
        if (items.isEmpty()) {
            ToastUtils.showShort("回收站为空");
            return;
        }
        dismissRecycleBinWindow();
        List<String> labelList = new ArrayList<>();
        for (RecycleItem item : items) {
            labelList.add(item.label);
        }
        try {
            recycleBinWindow = new ListPopupWindow(this);
            recycleBinWindow.setAnchorView(binding.btnRecycle);
            recycleBinWindow.setAdapter(new ArrayAdapter<>(this, R.layout.sp_item_dropdown, labelList));
            recycleBinWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_white));
            recycleBinWindow.setContentWidth(SizeUtils.dp2px(360));
            recycleBinWindow.setModal(true);
            recycleBinWindow.setOnItemClickListener((parent, view, position, itemId) -> {
                dismissRecycleBinWindow();
                if (position < 0 || position >= items.size()) {
                    return;
                }
                showRecycleItemDialog(items.get(position));
            });
            recycleBinWindow.show();
        } catch (Exception e) {
            Logger.e("回收站下拉框显示失败", e);
            recycleBinWindow = null;
            ToastUtils.showShort("回收站打开失败");
        }
    }

    private void dismissRecycleBinWindow() {
        if (recycleBinWindow != null) {
            try {
                if (recycleBinWindow.isShowing()) {
                    recycleBinWindow.dismiss();
                }
            } catch (Exception e) {
                Logger.e("回收站下拉框关闭失败", e);
            }
            recycleBinWindow = null;
        }
    }

    /**
     * 读取回收站内容，同时清理数据库中已不存在的无效记录
     */
    private List<RecycleItem> loadRecycleItems() {
        List<RecycleItem> items = new ArrayList<>();
        //工程
        Set<Long> recycledProjectIds = RecycleBinUtils.getRecycledProjectIds();
        Set<Long> validProjectIds = new HashSet<>();
        for (Long id : recycledProjectIds) {
            ProjectBean bean = GreenDaoHelper.getDaoSession(this).getProjectBeanDao().load(id);
            if (bean == null) {
                continue;
            }
            validProjectIds.add(id);
            items.add(new RecycleItem(true, id, "【工程】" + safeText(bean.name)));
        }
        //构件
        Set<Long> recycledZoneIds = RecycleBinUtils.getRecycledZoneIds();
        Set<Long> validZoneIds = new HashSet<>();
        for (Long id : recycledZoneIds) {
            ZoneBean bean = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().load(id);
            if (bean == null) {
                continue;
            }
            validZoneIds.add(id);
            //所属工程已在回收站时，构件随工程一起恢复，不单独展示
            if (validProjectIds.contains(bean.projectId) || RecycleBinUtils.isProjectRecycled(bean.projectId)) {
                continue;
            }
            items.add(new RecycleItem(false, id, "【构件】" + safeText(bean.projectName) + " / " + safeText(bean.number)));
        }
        if (validProjectIds.size() != recycledProjectIds.size()) {
            RecycleBinUtils.retainProjects(validProjectIds);
        }
        if (validZoneIds.size() != recycledZoneIds.size()) {
            RecycleBinUtils.retainZones(validZoneIds);
        }
        return items;
    }

    private void showRecycleItemDialog(RecycleItem item) {
        if (item == null || item.id == null) {
            return;
        }
        StyleAlertDialog styleAlertDialog = new StyleAlertDialog(this);
        styleAlertDialog.setContent(item.label + "\n\n恢复：重新显示在列表中\n删除：彻底删除，不可恢复")
                .setLeftButton("恢复", v -> {
                    styleAlertDialog.dismiss();
                    restoreRecycleItem(item);
                })
                .setRightButton("删除", v -> {
                    styleAlertDialog.dismiss();
                    confirmDeleteForever(item);
                });
        showDialogSafely(styleAlertDialog);
    }

    private void confirmDeleteForever(RecycleItem item) {
        StyleAlertDialog styleAlertDialog = new StyleAlertDialog(this);
        styleAlertDialog.setContent("确定彻底删除 " + item.label + " ？\n该操作不可恢复！")
                .setLeftButton("取消", v -> styleAlertDialog.dismiss())
                .setRightButton("确定", v -> {
                    styleAlertDialog.dismiss();
                    deleteForever(item);
                });
        showDialogSafely(styleAlertDialog);
    }

    private void restoreRecycleItem(RecycleItem item) {
        if (item.isProject) {
            RecycleBinUtils.removeProjects(Collections.singletonList(item.id));
            //恢复工程时，其下构件一并恢复
            Set<Long> zoneIds = new HashSet<>();
            List<ZoneBean> zoneBeanList = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().queryBuilder()
                    .where(ZoneBeanDao.Properties.ProjectId.eq(item.id)).list();
            if (zoneBeanList != null) {
                for (ZoneBean bean : zoneBeanList) {
                    if (bean != null && bean.id != null) {
                        zoneIds.add(bean.id);
                    }
                }
            }
            RecycleBinUtils.removeZones(zoneIds);
        } else {
            RecycleBinUtils.removeZones(Collections.singletonList(item.id));
        }
        queryPageListProject();
        queryShowZoneList();
        ToastUtils.showShort("已恢复");
    }

    private void deleteForever(RecycleItem item) {
        if (item.isProject) {
            deleteProjectsForever(Collections.singletonList(item.id));
        } else {
            deleteZonesForever(Collections.singletonList(item.id));
        }
        queryPageListProject();
        queryShowZoneList();
        if (projectListAdapter.getItems().isEmpty()) {
            SPUtils.getInstance().put("last-config-ZoneBean", "");
        }
        ToastUtils.showShort("已彻底删除");
    }

    /**
     * 彻底删除构件：清理图片缓存 + 数据库删除 + 回收站记录清理
     */
    private void deleteZonesForever(Collection<Long> zoneIds) {
        Set<Long> ids = new HashSet<>();
        if (zoneIds != null) {
            for (Long id : zoneIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            deleteZoneImageCache(GreenDaoHelper.getDaoSession(this).getZoneBeanDao().load(id));
        }
        GreenDaoHelper.getDaoSession(this).getZoneBeanDao().deleteByKeyInTx(ids);
        RecycleBinUtils.removeZones(ids);
        treatZoneIdSet.removeAll(ids);
        if (zoneBean != null && ids.contains(zoneBean.id)) {
            zoneBean = null;
            refreshTextView(false);
        }
    }

    /**
     * 彻底删除工程：级联删除其下所有构件
     */
    private void deleteProjectsForever(Collection<Long> projectIds) {
        Set<Long> ids = new HashSet<>();
        if (projectIds != null) {
            for (Long id : projectIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Set<Long> zoneIds = new HashSet<>();
        for (Long projectId : ids) {
            List<ZoneBean> zoneBeanList = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().queryBuilder()
                    .where(ZoneBeanDao.Properties.ProjectId.eq(projectId)).list();
            if (zoneBeanList != null) {
                for (ZoneBean bean : zoneBeanList) {
                    if (bean != null && bean.id != null) {
                        zoneIds.add(bean.id);
                    }
                }
            }
            GreenDaoHelper.getDaoSession(this).getProjectBeanDao().deleteByKey(projectId);
        }
        deleteZonesForever(zoneIds);
        RecycleBinUtils.removeProjects(ids);
        if (projectBean != null && ids.contains(projectBean.id)) {
            projectBean = null;
            zoneBean = null;
            refreshTextView(false);
        }
    }

    private void deleteZoneImageCache(ZoneBean bean) {
        if (bean == null || TextUtils.isEmpty(bean.number)) {
            return;
        }
        if (bean.f230ZoneBean == null || bean.f230ZoneBean.zoneCount <= 0) {
            return;
        }
        String strDir = StaticConstant.dataFilePathCache + "image_native/" + bean.number + "/";
        try {
            File fileDir = new File(strDir);
            if (fileDir.exists()) {
                DeleteFileUtils.deleteDirectory(strDir);
            }
        } catch (Exception e) {
            Logger.e("删除图片缓存失败：" + strDir, e);
        }
    }

    //---------------------对话框--------------------------
    private void showDialogSafely(StyleAlertDialog dialog) {
        if (dialog == null || isFinishing() || isDestroyed()) {
            return;
        }
        dismissCurrentDialog();
        try {
            dialog.show();
            currentDialog = dialog;
        } catch (Exception e) {
            Logger.e("对话框显示失败", e);
            currentDialog = null;
        }
    }

    private void dismissCurrentDialog() {
        if (currentDialog != null) {
            try {
                if (currentDialog.isShowing()) {
                    currentDialog.dismiss();
                }
            } catch (Exception e) {
                Logger.e("对话框关闭失败", e);
            }
            currentDialog = null;
        }
    }

    /**
     * 回收站条目
     */
    private static class RecycleItem {
        private final boolean isProject;
        private final Long id;
        private final String label;

        private RecycleItem(boolean isProject, Long id, String label) {
            this.isProject = isProject;
            this.id = id;
            this.label = label;
        }
    }

    //---------------------USB--------------------------
    private void initUsbHelper() {
        //U盘只能识别Pad格式化的U盘或格式化选FAT32的文件系统
        StaticConstant.usbHelper = new UsbHelper(this, new USBBroadCastReceiver.UsbListener() {
            @Override
            public void insertUsb(UsbDevice device_add) {
                ToastUtils.showShort("U盘插入");
                initCurrentFolder();
            }

            @Override
            public void removeUsb(UsbDevice device_remove) {
                ToastUtils.showShort("U盘被拔出");
                if (StaticConstant.usbHelper != null) {
                    StaticConstant.usbHelper.rootFolder = null;
                }
            }

            @Override
            public void getReadUsbPermission(UsbDevice usbDevice) {
                initCurrentFolder();
            }

            @Override
            public void failedReadUsb(UsbDevice usbDevice) {
            }
        });
        initCurrentFolder();
    }

    private final CompositeDisposable mRxEvent = new CompositeDisposable();

    private void initCurrentFolder() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Observable<Integer> observableIdentificationUsb = Observable.create(e -> {
            UsbHelper usbHelper = StaticConstant.usbHelper;
            if (usbHelper == null) {
                e.onComplete();
                return;
            }
            //getDeviceList() 在 context/usbManager 异常时会返回 null，必须判空
            UsbMassStorageDevice[] usbMassStorageDevices = usbHelper.getDeviceList();
            if (usbMassStorageDevices != null && usbMassStorageDevices.length > 0) {
                if (usbHelper.rootFolder == null) {
                    usbHelper.readDevice(usbMassStorageDevices[0]);
                }
                if (usbHelper.rootFolder != null) {
                    ToastUtils.showShort("U盘识别成功");
                }
            }
            e.onComplete();
        });
        Disposable disposable = observableIdentificationUsb.subscribeOn(Schedulers.io())
                .subscribe(integer -> {
                }, throwable -> Logger.e("U盘识别异常", throwable));
        mRxEvent.add(disposable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissRecycleBinWindow();
        dismissCurrentDialog();
        mRxEvent.clear();
        if (StaticConstant.usbHelper != null) {
            StaticConstant.usbHelper.finishUsbHelper();
        }
    }
}