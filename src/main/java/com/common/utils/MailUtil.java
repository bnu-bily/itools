package com.common.utils;

import com.common.bean.Test;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.velocity.VelocityEngineUtils;

import javax.annotation.Resource;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class MailUtil implements InitializingBean {

    private static final String DEFAULT_CHARSET = "UTF-8";

    @Value("${mail.from}")
    private String mailFrom;

    @Value("${mail.hosts}")
    private String mailHost;

    @Value("${mail.pwd}")
    private String pwd;

    @Value("${mail.user}")
    private String user;

    private Authenticator authenticator;

    @Resource
    private VelocityEngine velocityEngine;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.authenticator = new DefaultAuthenticator(user, pwd);
    }

    private HtmlEmail generateBaseEmail(String subject, List<String> toList, List<String> ccList) throws EmailException {
        HtmlEmail email = new HtmlEmail();
        email.setCharset(DEFAULT_CHARSET);
        email.setHostName(mailHost);
        email.setStartTLSEnabled(true);
        email.setAuthenticator(authenticator);
        email.setFrom(mailFrom);
        email.setSubject(subject);
        if (toList != null && CollectionUtils.size(toList) > 0) {
            email.addTo(toList.toArray(new String[toList.size()]));
        }
        if (ccList != null && CollectionUtils.size(ccList) > 0) {
            email.addCc(ccList.toArray(new String[ccList.size()]));
        }
        return email;
    }

    /**
     * 简易发邮件
     *
     * @param params       参数
     * @param templateName 模板名称
     * @param subject      主题
     * @param toEmails     to
     * @param ccEmails     cc
     */
    public void sendSimpleEmail(Map<String, Object> params, String templateName, String subject,
                                List<String> toEmails, List<String> ccEmails) throws Exception {
        HtmlEmail email = generateBaseEmail(subject, toEmails, ccEmails);
        String content = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, templateName, DEFAULT_CHARSET, params);
        email.setHtmlMsg(content);
        email.send();
    }

    /**
     * 本地发送邮件 不需要密码 html格式
     *
     * @param params       参数
     * @param templateName 模板名称
     * @param subject      主题
     * @param toEmails     to
     * @param ccEmails     cc
     * @throws Exception
     */
    public void sendLocalEmail(Map<String, Object> params, String templateName, String subject,
                               List<String> toEmails, List<String> ccEmails) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.localhost", "127.0.0.1");
        properties.setProperty("mail.smtp.auth", "false");
        //根据需求设置true或false
        properties.setProperty("mail.smtp.socketFactory.fallback", "false");
        properties.setProperty("mail.smtp.starttls.enable", "true");

        String content = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, templateName, DEFAULT_CHARSET, params);

        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailFrom, null);
            }
        };

        Session session = Session.getDefaultInstance(properties, authenticator);
        MimeMessage msg = new MimeMessage(session);
        //发件人
        msg.setFrom(new InternetAddress(mailFrom));
        msg.setSubject(subject);
        msg.setContent(content, "text/html;charset=utf-8");
        List<InternetAddress> addresses = Lists.newArrayList();
        for (String rec : toEmails) {
            addresses.add(new InternetAddress(rec));
        }
        msg.setRecipients(Message.RecipientType.TO, addresses.toArray(new Address[addresses.size()]));//收件人

        Transport.send(msg);
    }


    /**
     * 发送邮件+附件
     */
    public void sendEmailWithAttachment(List<String> toList,
                                        String subject,
                                        String content, List<File> attachments) {


        Preconditions.checkArgument(CollectionUtils.isNotEmpty(toList), "收邮件人列表不能为空");
        try {
            HtmlEmail email = generateBaseEmail(subject, toList, Lists.newArrayList());
            email.setMsg(content);
            if (CollectionUtils.isNotEmpty(attachments)) {
                for (File file : attachments) {
                    EmailAttachment emailAttachment = new EmailAttachment();
                    emailAttachment.setPath(file.getPath());
                    emailAttachment.setName(new String(file.getName().getBytes("utf-8"), "ISO8859-1"));
                    email.attach(emailAttachment);
                }
            } else {
                //"附件为空";
            }
            email.send();
        } catch (Exception e) {
            throw new RuntimeException("发送邮件失败");
        }
        // success";
    }

    public void testMail(List<Test> groups) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean success = false;
        try {
            List<String> toEmails = Lists.newArrayList();

            Map<String, Object> params = Maps.newHashMap();
            params.put("groups", groups);
            sendLocalEmail(params, "test.vm", "test", toEmails, Lists.newArrayList());
            success = true;
        } catch (Exception e) {

            success = false;
        } finally {
            long cost = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        }
    }
}
