# Spring事务管理入门指南

Spring框架提供了强大而灵活的事务管理功能，支持声明式事务和编程式事务。本文档将介绍如何使用XML配置和注解两种方式来配置Spring事务管理。

## 项目结构概览

```
transaction-demo/
├── src/main/java/com/dai
│   ├── config/SpringConfig.java          # 注解配置类
│   ├── mapper/AccountMapper.java         # 数据访问接口
│   ├── service/AccountService.java       # 业务接口
│   ├── service/impl/AccountServiceImpl.java # 业务实现类
│   └── AccountTest.java                  # 测试类
├── src/main/resources
│   ├── applicationContext.xml            # XML配置文件
│   ├── applicationContext2.xml           # XML配置文件（注解驱动）
│   └── jdbc.properties                   # 数据库配置文件
└── pom.xml                               # Maven依赖配置
```


## 1. Maven依赖配置

在`pom.xml`中添加事务管理所需的依赖：

```xml
<dependencies>
    <!-- Spring核心依赖 -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
        <version>5.3.7</version>
    </dependency>
    
    <!-- Spring JDBC事务管理 -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-jdbc</artifactId>
        <version>5.2.13.RELEASE</version>
    </dependency>
    
    <!-- 数据库连接池 -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>druid</artifactId>
        <version>1.1.23</version>
    </dependency>
    
    <!-- MySQL驱动 -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>5.1.49</version>
    </dependency>
    
    <!-- AOP支持（事务管理需要） -->
    <dependency>
        <groupId>org.aspectj</groupId>
        <artifactId>aspectjweaver</artifactId>
        <version>1.9.6</version>
    </dependency>
</dependencies>
```


## 2. 数据访问层配置

### AccountMapper接口

```java
package com.dai.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AccountMapper {
    // 增加金额
    @Update("update tb_account set money=money+#{money} where account_name=#{accountName}")
    public void incrMoney(@Param("accountName") String accountName, @Param("money") Integer money);
    
    // 减少金额
    @Update("update tb_account set money=money-#{money} where account_name=#{accountName}")
    public void decrMoney(@Param("accountName") String accountName, @Param("money") Integer money);
}
```


## 3. 业务层配置

### AccountService接口

```java
package com.dai.service;

public interface AccountService {
    void transferMoney(String outAccount, String inAccount, Integer money);
}
```


### AccountServiceImpl实现类

```java
package com.dai.service.impl;

import com.dai.mapper.AccountMapper;
import com.dai.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("accountService")
@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRED, timeout=30)
    public void transferMoney(String outAccount, String inAccount, Integer money) {
        accountMapper.decrMoney(outAccount, money);
        int i = 1/0; // 故意制造异常，测试事务回滚
        accountMapper.incrMoney(inAccount, money);
    }
}
```


## 4. XML配置方式

### 完整的XML配置 (applicationContext.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xmlns:tx="http://www.springframework.org/schema/tx"
       xsi:schemaLocation="
       http://www.springframework.org/schema/beans
       http://www.springframework.org/schema/beans/spring-beans.xsd
       http://www.springframework.org/schema/context
       http://springframework.org/schema/context/spring-context.xsd
       http://www.springframework.org/schema/aop
       http://www.springframework.org/schema/aop/spring-aop.xsd
       http://www.springframework.org/schema/tx
       http://www.springframework.org/schema/tx/spring-tx.xsd
       ">

    <!-- 组件扫描 -->
    <context:component-scan base-package="com.dai"/>

    <!-- 加载properties文件 -->
    <context:property-placeholder location="classpath:jdbc.properties"/>

    <!-- 配置数据源 -->
    <bean id="dataSource" class="com.alibaba.druid.pool.DruidDataSource">
        <property name="driverClassName" value="${jdbc.driver}"/>
        <property name="url" value="${jdbc.url}"/>
        <property name="username" value="${jdbc.username}"/>
        <property name="password" value="${jdbc.password}"/>
    </bean>

    <!-- 配置SqlSessionFactoryBean -->
    <bean class="org.mybatis.spring.SqlSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <!-- Mapper扫描配置 -->
    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
        <property name="basePackage" value="com.dai.mapper"/>
    </bean>

    <!-- 配置平台事务管理器 -->
    <bean id="transactionManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <!-- 配置事务Advice -->
    <tx:advice id="txAdvice" transaction-manager="transactionManager">
        <tx:attributes>
            <!-- 配置不同的方法的事务属性 -->
            <tx:method name="transferMoney"
                       isolation="READ_COMMITTED"
                       propagation="REQUIRED"
                       timeout="3"
                       read-only="false"/>
            <tx:method name="registAccount"/>
            <tx:method name="add*"/>
            <tx:method name="update*"/>
            <tx:method name="delete*"/>
            <tx:method name="select*"/>
            <tx:method name="*"/>
        </tx:attributes>
    </tx:advice>

    <!-- AOP配置 -->
    <aop:config>
        <!-- 配置切点表达式 -->
        <aop:pointcut id="txPointcut" expression="execution(* com.dai.service.impl.*.*(..))"/>
        <!-- 配置织入关系 -->
        <aop:advisor advice-ref="txAdvice" pointcut-ref="txPointcut"/>
    </aop:config>
</beans>
```


## 5. 注解配置方式

### 配置类 (SpringConfig.java)

```java
package com.dai.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@ComponentScan("com.dai")
@PropertySource("classpath:jdbc.properties")
@MapperScan("com.dai.mapper")
@EnableTransactionManagement  // 启用事务管理
public class SpringConfig {

    @Bean
    public DataSource dataSource(
            @Value("${jdbc.driver}") String driver,
            @Value("${jdbc.url}") String url,
            @Value("${jdbc.username}") String username,
            @Value("${jdbc.password}") String password
    ){
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setDriverClassName(driver);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource dataSource){
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(dataSource);
        return sqlSessionFactoryBean;
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource){
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
        dataSourceTransactionManager.setDataSource(dataSource);
        return dataSourceTransactionManager;
    }
}
```


### 简化版XML配置 (applicationContext2.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:tx="http://www.springframework.org/schema/tx"
       xsi:schemaLocation="
       http://www.springframework.org/schema/beans
       http://www.springframework.org/schema/beans/spring-beans.xsd
       http://www.springframework.org/schema/context
       http://www.springframework.org/schema/context/spring-context.xsd
       http://www.springframework.org/schema/tx
       http://www.springframework.org/schema/tx/spring-tx.xsd
       ">

    <!-- 组件扫描 -->
    <context:component-scan base-package="com.dai"/>

    <!-- 加载properties文件 -->
    <context:property-placeholder location="classpath:jdbc.properties"/>

    <!-- 配置数据源 -->
    <bean id="dataSource" class="com.alibaba.druid.pool.DruidDataSource">
        <property name="driverClassName" value="${jdbc.driver}"/>
        <property name="url" value="${jdbc.url}"/>
        <property name="username" value="${jdbc.username}"/>
        <property name="password" value="${jdbc.password}"/>
    </bean>

    <!-- 配置SqlSessionFactoryBean -->
    <bean class="org.mybatis.spring.SqlSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <!-- Mapper扫描配置 -->
    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
        <property name="basePackage" value="com.dai.mapper"/>
    </bean>

    <!-- 配置平台事务管理器 -->
    <bean id="transactionManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <!-- 事务的注解驱动 -->
    <tx:annotation-driven transaction-manager="transactionManager"/>
</beans>
```


## 6. 数据库配置文件

### jdbc.properties

```properties
jdbc.driver=com.mysql.jdbc.Driver
jdbc.url=jdbc:mysql://localhost:3306/mybatis
jdbc.username=root
jdbc.password=root
```


## 7. 测试类

### AccountTest.java

```java
package com.dai;

import com.dai.service.AccountService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AccountTest {
    public static void main(String[] args) {
        // XML方式创建IOC容器
        ApplicationContext app = new ClassPathXmlApplicationContext("applicationContext2.xml");
        // 纯注解方式创建IOC容器
        // ApplicationContext app = new AnnotationConfigApplicationContext(SpringConfig.class);
        
        AccountService accountService = app.getBean(AccountService.class);
        accountService.transferMoney("tom", "lucy", 500);  // 测试事务功能
    }
}
```


## 8. 事务配置参数说明

### 事务属性详解

- **isolation**：事务隔离级别，解决事务并发问题
    - `Isolation.DEFAULT`：使用数据库默认隔离级别
    - `Isolation.READ_COMMITTED`：读已提交
    - `Isolation.REPEATABLE_READ`：可重复读
    - `Isolation.SERIALIZABLE`：串行化

- **propagation**：事务传播行为
    - `Propagation.REQUIRED`：如果当前存在事务，则加入该事务；如果不存在，则创建一个新的事务
    - `Propagation.REQUIRES_NEW`：创建一个新的事务，如果当前存在事务，则将当前事务挂起
    - `Propagation.SUPPORTS`：如果当前存在事务，则加入该事务；如果不存在，则以非事务方式继续运行

- **timeout**：超时时间，单位是秒，默认-1表示不设置超时时间

- **read-only**：是否只读，查询操作设置为true可提高性能

## 9. 总结

Spring事务管理提供了两种主要配置方式：

1. **XML配置方式**：通过`<tx:advice>`和`<aop:config>`进行详细配置，适合复杂事务需求
2. **注解配置方式**：通过`@EnableTransactionManagement`和`@Transactional`注解简化配置，更直观易用

无论使用哪种方式，都需要：
- 配置`DataSourceTransactionManager`作为事务管理器
- 在需要事务的方法或类上添加`@Transactional`注解
- 确保有AOP支持（aspectjweaver依赖）

在测试代码中，由于`transferMoney`方法中故意添加了`int i = 1/0;`异常，事务会回滚，确保数据一致性。