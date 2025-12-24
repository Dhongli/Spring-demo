package com.dai;

import com.dai.service.AccountService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AccountTest {

    public static void main(String[] args) {
        // xml方式创建IOC容器
        ApplicationContext app = new ClassPathXmlApplicationContext("applicationContext2.xml");
        // 纯注解方式创建IOC容器
        // ApplicationContext app = new AnnotationConfigApplicationContext(SpringConfig.class);
        AccountService accountService = app.getBean(AccountService.class);
        accountService.transferMoney("tom","lucy",500);
    }

}
