package com.example.gtj_f230_rebound_corrosion.base.activity.data;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.gtj_f230_rebound_corrosion.base.adapter.AssessmentRoundAdapter;
import com.example.gtj_f230_rebound_corrosion.base.assessment.AssessmentEngine;
import com.example.gtj_f230_rebound_corrosion.base.model.ZoneBean;
import com.example.gtj_f230_rebound_corrosion.base.storage.GreenDaoHelper;
import com.example.gtj_f230_rebound_corrosion.base.utils.RecycleBinUtils;
import com.example.gtj_f230_rebound_corrosion.base.utils.SpinnerUtils;
import com.example.gtj_f230_rebound_corrosion.databinding.FragmentAssessmentBinding;
import com.example.gtj_f230_rebound_corrosion.functions.database.greenDao.db.ZoneBeanDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 综合评估页签：轮次筛选 + 测点轮次评估表
 */
@SuppressLint("SetTextI18n")
public class AssessmentFragment extends Fragment {

    private FragmentAssessmentBinding binding;
    private ZoneBean zoneBean;
    //当前工程下可评估的构件（轮次）
    private final List<ZoneBean> zoneList = new ArrayList<>();
    private final List<String> roundNames = new ArrayList<>();
    private AssessmentRoundAdapter adapter;
    private int selectedRound = 0;
    private long filterZoneId = -1;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        zoneBean = ((DataDetailsActivity) context).getZoneBean();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAssessmentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //页面重建时 onAttach 可能早于宿主赋值 zoneBean，这里兜底重新获取，避免空指针
        if (zoneBean == null && getActivity() instanceof DataDetailsActivity) {
            zoneBean = ((DataDetailsActivity) getActivity()).getZoneBean();
        }
        adapter = new AssessmentRoundAdapter();
        binding.rvRounds.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvRounds.setAdapter(adapter);
        adapter.setOnViewEvalListener(row -> {
            Intent intent = new Intent(getContext(), AssessmentActivity.class);
            intent.putExtra(AssessmentActivity.EXTRA_ZONE_ID, row.zoneId);
            intent.putExtra(AssessmentActivity.EXTRA_POINT_INDEX, row.pointIndex);
            startActivity(intent);
        });
        binding.btnFilter.setOnClickListener(v -> {
            filterZoneId = selectedRound == 0 || selectedRound > zoneList.size() ? -1 : zoneList.get(selectedRound - 1).id;
            refreshRows();
        });
        loadZones();
        refreshRows();
    }

    @Override
    public void onResume() {
        super.onResume();
        //评估页可能录入了设计强度，返回时刷新
        refreshRows();
    }

    private void loadZones() {
        zoneList.clear();
        roundNames.clear();
        roundNames.add("全部轮次");
        if (zoneBean == null) {
            return;
        }
        Set<Long> recycledZoneIds = RecycleBinUtils.getRecycledZoneIds();
        List<ZoneBean> zoneBeanList = GreenDaoHelper.getDaoSession(requireContext()).getZoneBeanDao().queryBuilder()
                .where(ZoneBeanDao.Properties.ProjectId.eq(zoneBean.projectId))
                .orderAsc(ZoneBeanDao.Properties.Id)
                .list();
        if (zoneBeanList != null) {
            for (ZoneBean bean : zoneBeanList) {
                if (bean == null || bean.id == null || recycledZoneIds.contains(bean.id)) {
                    continue;
                }
                zoneList.add(bean);
                roundNames.add("构件" + bean.number);
            }
        }
        SpinnerUtils.attachDataSource(binding.spRound, roundNames, (spinner, text, position) -> selectedRound = position);
    }

    private void refreshRows() {
        if (binding == null || zoneBean == null) {
            return;
        }
        List<ZoneBean> targets = new ArrayList<>();
        for (ZoneBean bean : zoneList) {
            if (filterZoneId == -1 || bean.id == filterZoneId) {
                targets.add(bean);
            }
        }
        List<AssessmentEngine.RoundRow> rows = AssessmentEngine.buildRows(targets, AssessmentEngine.getDesignStrength(zoneBean.projectId));
        adapter.submitList(rows);
        boolean empty = rows.isEmpty();
        binding.tvNull.setVisibility(empty ? VISIBLE : GONE);
        binding.rvRounds.setVisibility(empty ? GONE : VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
