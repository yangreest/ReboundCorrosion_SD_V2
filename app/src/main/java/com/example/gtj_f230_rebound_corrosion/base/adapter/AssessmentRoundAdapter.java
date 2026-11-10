package com.example.gtj_f230_rebound_corrosion.base.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.chad.library.adapter4.BaseQuickAdapter;
import com.chad.library.adapter4.viewholder.QuickViewHolder;
import com.example.gtj_f230_rebound_corrosion.R;
import com.example.gtj_f230_rebound_corrosion.base.assessment.AssessmentEngine;

/**
 * 综合评估表格适配器：轮次/测点行
 */
public class AssessmentRoundAdapter extends BaseQuickAdapter<AssessmentEngine.RoundRow, QuickViewHolder> {

    public interface OnViewEvalListener {
        void onViewEval(AssessmentEngine.RoundRow row);
    }

    private OnViewEvalListener onViewEvalListener;

    public void setOnViewEvalListener(OnViewEvalListener listener) {
        this.onViewEvalListener = listener;
    }

    @Override
    protected QuickViewHolder onCreateViewHolder(Context context, ViewGroup parent, int viewType) {
        return new QuickViewHolder(LayoutInflater.from(context).inflate(R.layout.item_assess_round, parent, false));
    }

    @Override
    protected void onBindViewHolder(QuickViewHolder holder, int position, AssessmentEngine.RoundRow item) {
        holder.setText(R.id.tv_row_title, "构件" + item.number + " / 测点" + (item.pointIndex + 1));
        holder.setText(R.id.tv_row_sub, item.sub);
        holder.setText(R.id.tv_crack, item.crackText);
        holder.setText(R.id.tv_strength, item.strengthText);
        holder.setText(R.id.tv_corrosion, item.corrosionText);
        holder.setText(R.id.tv_status, item.n + "/3 项");
        holder.setText(R.id.tv_state, item.state);
        holder.getView(R.id.btn_view).setOnClickListener(v -> {
            if (onViewEvalListener != null) {
                onViewEvalListener.onViewEval(item);
            }
        });
    }
}
