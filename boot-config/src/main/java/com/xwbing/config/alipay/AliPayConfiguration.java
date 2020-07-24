package com.xwbing.config.alipay;

import javax.annotation.Resource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;

import lombok.extern.slf4j.Slf4j;

/**
 * @author daofeng
 * @version $Id$
 * @since 2020年07月24日 下午9:57
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AliPayProperties.class)
public class AliPayConfiguration {
    @Resource
    private AliPayProperties aliPayProperties;

    @Bean(name = "aliPayClient")
    public AlipayClient getAliPayClient() {
        return new DefaultAlipayClient(aliPayProperties.getServerUrl(), aliPayProperties.getAppId(),
                aliPayProperties.getAppPrivateKey(), "json", "UTF-8", aliPayProperties.getAliPayPublicKey(), "RSA2");

    }

    @Bean(name = "aliPayCertClient")
    @ConditionalOnExpression("!'${aliPay.certificatePath:}'.empty")
    public AlipayClient getAliPayCertClient() {
        try {
            CertAlipayRequest certAlipayRequest = new CertAlipayRequest();
            certAlipayRequest.setServerUrl(aliPayProperties.getServerUrl());
            certAlipayRequest.setAppId(aliPayProperties.getAppId());
            certAlipayRequest.setPrivateKey(aliPayProperties.getAppPrivateKey());
            certAlipayRequest.setFormat("json");
            certAlipayRequest.setCharset("UTF-8");
            certAlipayRequest.setSignType("RSA2");
            certAlipayRequest.setCertPath(aliPayProperties.getCertificatePath() + "/appCertPublicKey.crt");
            certAlipayRequest
                    .setAlipayPublicCertPath(aliPayProperties.getCertificatePath() + "/alipayCertPublicKey_RSA2.crt");
            certAlipayRequest.setRootCertPath(aliPayProperties.getCertificatePath() + "/alipayRootCert.crt");
            return new DefaultAlipayClient(certAlipayRequest);
        } catch (Exception e) {
            log.error("initAliPayCertClient error", e);
            return null;
        }
    }
}
