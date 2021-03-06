package com.xwbing.service.pay.vo;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一收单交易创建参数
 *
 * @author xwbing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliPayTradeCreateParam {
    /**
     * 商户订单号
     */
    private String outTradeNo;
    /**
     * 总金额
     * 单位为元，精确到小数点后两位
     * 取值范围[0.01,100000000]
     */
    private BigDecimal totalAmount;
    /**
     * 订单标题
     */
    private String subject;
    /**
     * 买家的支付宝唯一用户号
     */
    private String buyerId;
    private AliPayExtendParam extendParam;

    public static AliPayTradeCreateParam build(String outTradeNo, String buyerId, String subject,
            BigDecimal totalAmount) {
        return AliPayTradeCreateParam.builder().outTradeNo(outTradeNo).subject(subject).totalAmount(totalAmount)
                .buyerId(buyerId).build();
    }

    public static String checkParam(AliPayTradeCreateParam param) {
        String message;
        if (StringUtils.isEmpty(param.getOutTradeNo())) {
            message = "商户订单号为空";
        } else if (StringUtils.isEmpty(param.getSubject())) {
            message = "订单标题为空";
        } else if (param.getTotalAmount().compareTo(BigDecimal.valueOf(0.01)) == -1) {
            message = "金额必须大于等于0.01元";
        } else if (StringUtils.isEmpty(param.getBuyerId())) {
            message = "买家用户号不能为空";
        } else {
            message = StringUtils.EMPTY;
        }
        return message;
    }
}
