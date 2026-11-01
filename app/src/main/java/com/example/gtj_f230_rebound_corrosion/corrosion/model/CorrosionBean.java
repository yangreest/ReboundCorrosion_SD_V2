package com.example.gtj_f230_rebound_corrosion.corrosion.model;

import androidx.annotation.NonNull;

import com.example.gtj_f230_rebound_corrosion.base.utils.StringUtils;

import java.io.Serializable;

public class CorrosionBean implements Serializable {
    public String distance;  //距离根部
    public String designSteelDameter;  //设计钢筋直径
    public String diameter;  //直径
    public String strCheckArea;  //截面积/锈蚀剩余直径
    public String corrosion;  //锈蚀度
    public String corrosion1;  //锈蚀度（截面积）

    public CorrosionBean(String distance, String designSteelDameter, String diameter) {
        this.distance = distance;
        this.designSteelDameter = designSteelDameter;
        this.diameter = diameter;
        double corr = Double.parseDouble(diameter) / Double.parseDouble(designSteelDameter) * 100;
        this.corrosion = ((int) (100d - corr)) + "";
        double designR = Double.parseDouble(designSteelDameter) / 2;
        double checkR = Double.parseDouble(diameter) / 2;
        //截面积
        double designArea = StringUtils.getRounding(Math.PI * designR * designR, 2);
        double checkArea = StringUtils.getRounding(Math.PI * checkR * checkR, 2);
        double corr1 = checkArea / designArea * 100;
        corrosion1 = ((int) (100d - corr1)) + "";
        strCheckArea = checkArea + "";
    }

    public CorrosionBean(String distance, String designSteelDameter, String diameter,String corrosion1) {
        this.distance = distance; // 距离根部
        this.designSteelDameter = designSteelDameter; // 设计钢筋直径
        this.diameter = diameter; // 钢筋直径
       // double corr = Double.parseDouble(diameter) / Double.parseDouble(designSteelDameter) * 100;
        this.corrosion1 = corrosion1;
        //corrosion1可能为非数值文本（如“无锈蚀”），此时按锈蚀度0计算，剩余直径即设计直径
        double corrosionRate = parseCorrosionRate(corrosion1);
        this.strCheckArea = Double.parseDouble(designSteelDameter) * (1.0- corrosionRate*0.01)+"";
//        double designR = Double.parseDouble(designSteelDameter) / 2;
//        double checkR = Double.parseDouble(diameter) / 2;
//        //截面积
//        double designArea = StringUtils.getRounding(Math.PI * designR * designR, 2);
//        double checkArea = StringUtils.getRounding(Math.PI * checkR * checkR, 2);
//        double corr1 = checkArea / designArea * 100;
//        corrosion1 = ((int) (100d - corr1)) + "";
        //strCheckArea = checkArea + "";
    }



    //解析锈蚀度文本，非数值（如“无锈蚀”）时返回0，避免NumberFormatException
    private static double parseCorrosionRate(String corrosion1) {
        try {
            return Double.parseDouble(corrosion1);
        } catch (NumberFormatException | NullPointerException e) {
            return 0d;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "{" + diameter + " , " + corrosion1 + "}";
    }
}
