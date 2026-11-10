package com.example.gtj_f230_rebound_corrosion.base.activity.data;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.blankj.utilcode.util.ToastUtils;
import com.example.gtj_f230_rebound_corrosion.R;
import com.example.gtj_f230_rebound_corrosion.StaticConstant;
import com.example.gtj_f230_rebound_corrosion.base.activity.BaseActivity;
import com.example.gtj_f230_rebound_corrosion.base.adapter.DataResultAdapter;
import com.example.gtj_f230_rebound_corrosion.base.model.ProjectBean;
import com.example.gtj_f230_rebound_corrosion.base.model.ZoneBean;
import com.example.gtj_f230_rebound_corrosion.base.storage.GreenDaoHelper;
import com.example.gtj_f230_rebound_corrosion.databinding.ActDataDetailsBinding;

import java.util.ArrayList;

//import com.example.gtj_f230_rebound_corrosion.R;
//import com.example.gtj_f230_rebound_corrosion.StaticConstant;
//import com.example.gtj_f230_rebound_corrosion.base.activity.BaseActivity;
//import com.example.gtj_f230_rebound_corrosion.base.adapter.DataResultAdapter;
//import com.example.gtj_f230_rebound_corrosion.base.model.ProjectBean;
//import com.example.gtj_f230_rebound_corrosion.base.model.ZoneBean;
//import com.example.gtj_f230_rebound_corrosion.databinding.ActDataDetailsBinding;
//
//import java.util.ArrayList;

/**
 * 数据管理
 */
@SuppressLint("SetTextI18n")
public class DataDetailsActivity extends BaseActivity<ActDataDetailsBinding> {
    //只传主键，避免 Intent 传递大对象触发 TransactionTooLargeException
    public static final String EXTRA_PROJECT_ID = "ProjectId";
    public static final String EXTRA_ZONE_ID = "ZoneId";
    private ZoneBean zoneBean;
    private int index;

    @Override
    protected ActDataDetailsBinding getBinding() {
        return ActDataDetailsBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initLabel() {
        initBatteryReceiver(binding.navigationBar);
        initDeviceStatusExecutor(binding.navigationBar);

        binding.navigationBar.setTitleText("检测数据");
    }

    @Override
    protected void initView() {
        ProjectBean projectBean = (ProjectBean) getIntent().getSerializableExtra("ProjectBean");
        //优先按主键从数据库读取最新数据
        zoneBean = resolveZoneBean();
        long projectId = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1L);
        if (projectId > 0) {
            ProjectBean dbProjectBean = GreenDaoHelper.getDaoSession(this).getProjectBeanDao().load(projectId);
            if (dbProjectBean != null) {
                projectBean = dbProjectBean;
            }
        }
        if (projectBean == null || zoneBean == null) {
            ToastUtils.showShort("数据不存在或已被删除");
            finish();
            return;
        }
        index = getIntent().getIntExtra("index", 0);
        ArrayList<Fragment> fragments = new ArrayList<>();
        fragments.add(new F230WidthFragment());
        fragments.add(new ReboundFragment());
        if (TextUtils.equals(StaticConstant.APP_TYPE_DEFAULT, StaticConstant.APP_CORROSION)) {
            fragments.add(new CorrosionFragment());
        } else {
            fragments.add(new ThicknessFragment());
        }
        fragments.add(new AssessmentFragment());
        DataResultAdapter adapter = new DataResultAdapter(this, fragments);
        binding.viewPager.setUserInputEnabled(false);
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        binding.tvF230.setSelected(true);
                        binding.tvRebound.setSelected(false);
                        binding.tvCorrosion.setSelected(false);
                        binding.tvAssessment.setSelected(false);
                        break;
                    case 1:
                        binding.tvF230.setSelected(false);
                        binding.tvRebound.setSelected(true);
                        binding.tvCorrosion.setSelected(false);
                        binding.tvAssessment.setSelected(false);
                        break;
                    case 2:
                        binding.tvF230.setSelected(false);
                        binding.tvRebound.setSelected(false);
                        binding.tvCorrosion.setSelected(true);
                        binding.tvAssessment.setSelected(false);
                        break;
                    case 3:
                        binding.tvF230.setSelected(false);
                        binding.tvRebound.setSelected(false);
                        binding.tvCorrosion.setSelected(false);
                        binding.tvAssessment.setSelected(true);
                        break;
                }
            }
        });
//        binding.tvF230.setSelected(true);

        binding.tvF230.setOnClickListener(this::onViewClicked);
        binding.tvRebound.setOnClickListener(this::onViewClicked);
        binding.tvCorrosion.setOnClickListener(this::onViewClicked);
        binding.tvAssessment.setOnClickListener(this::onViewClicked);
        binding.btnReturn.setOnClickListener(this::onViewClicked);
        boolean isCorrosion = TextUtils.equals(StaticConstant.APP_TYPE_DEFAULT, StaticConstant.APP_CORROSION);
        binding.tvCorrosion.setText(isCorrosion ? "锈蚀检测" : "保护层厚度检测");
        // Deleted:assert projectBean != null;
        binding.tvInfo.setText(getStringBuilder(projectBean, isCorrosion));
        binding.viewPager.post(() -> binding.viewPager.setCurrentItem(index, false));
    }

    @NonNull
    private StringBuilder getStringBuilder(ProjectBean projectBean, boolean isCorrosion) {
        StringBuilder sbd = new StringBuilder("工程名称：" + (projectBean == null || projectBean.name == null ? "" : projectBean.name));
        sbd.append("\n工程地址：").append(projectBean == null || projectBean.address == null ? "" : projectBean.address)
                .append("\n构件编号：").append(zoneBean == null || zoneBean.number == null ? "" : zoneBean.number)
                .append("\n构件备注：").append(zoneBean == null || zoneBean.remark == null ? "" : zoneBean.remark)
                .append("\n缝宽测点：").append(zoneBean == null || zoneBean.f230ZoneBean == null ? 0 : zoneBean.f230ZoneBean.zoneCount)
                .append("\n回弹测点：").append(zoneBean == null || zoneBean.rebounderZoneBean == null ? 0 : zoneBean.rebounderZoneBean.zoneCount);
        if (isCorrosion) {
            sbd.append("\n锈蚀测点：").append(zoneBean == null || zoneBean.corrosionZoneBean == null ? 0 : zoneBean.corrosionZoneBean.zoneCount);
        } else {
            sbd.append("\n保护层厚度测点：").append(zoneBean == null ? 0 : zoneBean.thicknessCount);
        }
        return sbd;
    }

    public void onViewClicked(View view) {
        if (view.getId() == R.id.tv_f230) {
            binding.tvF230.setSelected(true);
            binding.tvRebound.setSelected(false);
            binding.tvCorrosion.setSelected(false);
            binding.tvAssessment.setSelected(false);
            binding.viewPager.setCurrentItem(0);
        } else if (view.getId() == R.id.tv_rebound) {
            binding.tvF230.setSelected(false);
            binding.tvRebound.setSelected(true);
            binding.tvCorrosion.setSelected(false);
            binding.tvAssessment.setSelected(false);
            binding.viewPager.setCurrentItem(1);
        } else if (view.getId() == R.id.tv_corrosion) {
            binding.tvF230.setSelected(false);
            binding.tvRebound.setSelected(false);
            binding.tvCorrosion.setSelected(true);
            binding.tvAssessment.setSelected(false);
            binding.viewPager.setCurrentItem(2);
        } else if (view.getId() == R.id.tv_assessment) {
            binding.tvF230.setSelected(false);
            binding.tvRebound.setSelected(false);
            binding.tvCorrosion.setSelected(false);
            binding.tvAssessment.setSelected(true);
            binding.viewPager.setCurrentItem(3);
        } else if (view.getId() == R.id.btn_return) {
            finish();
        }
    }

    public ZoneBean getZoneBean() {
        //页面因配置变更/进程恢复而重建时，Fragment 会在 initView() 之前于
        //super.onCreate() 中恢复并回调 onAttach，此时 zoneBean 尚未赋值。
        //这里做惰性解析，保证任何时刻都能拿到有效构件，避免 Fragment 空指针崩溃。
        if (zoneBean == null) {
            zoneBean = resolveZoneBean();
        }
        return zoneBean;
    }

    /**
     * 从 Intent 解析构件：优先按主键查库取最新数据，其次回退到序列化对象。
     */
    private ZoneBean resolveZoneBean() {
        long zoneId = getIntent().getLongExtra(EXTRA_ZONE_ID, -1L);
        if (zoneId > 0) {
            ZoneBean dbZoneBean = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().load(zoneId);
            if (dbZoneBean != null) {
                return dbZoneBean;
            }
        }
        return (ZoneBean) getIntent().getSerializableExtra("ZoneBean");
    }
}
