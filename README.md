
## 项目描述
本项目是一个使用Spring Boot框架构建的校园店铺点评网，旨在展示如何使用Spring Boot进行快速开发。项目集成了多个常用的依赖和工具，包括RocketMQ、Redis、MySQL、MyBatis-Plus等。

---
## 项目背景
作为学生，我意识到在校园里找到合适的店铺是一项常见的需求。无论是寻找吃饭的地方、购买日用品还是寻找娱乐场所，学生们需要一个方便快捷的平台来查看其他用户对店铺的评价和推荐，以便做出更明智的选择。

在校园里，新生可能对周边的店铺不熟悉，他们需要一个可靠的参考来指导他们选择最适合自己需求的店铺。通过构建校园店铺点评网，我们可以为学生们提供大量的用户评价和评分，让他们了解每个店铺的特点和质量，从而更好地满足他们的需求。

---
## 技术栈 
- Spring Boot 2.3.12.RELEASE 
- Java 1.8 
- Apache RocketMQ 2.2.2 
- Spring Boot Starter Data Redis 2.6.2 
- Lettuce Core 6.1.6.RELEASE 
- Apache Commons Pool2 
- MySQL Connector Java 5.1.47 
- Lombok 
- MyBatis Plus 3.4.3 
- Hutool 5.7.17 
- AspectJ Weaver 
- Redisson 3.13.6
---
## 构建和运行
确保已安装Java 1.8及以上版本。
使用Maven构建项目：
~~~shell
mvn clean install
~~~
运行项目：
~~~shell
java -jar target/xxx.jar
~~~
---
## 注意事项
该项目依赖于RocketMQ、Redis和MySQL，请确保安装并正确配置这些服务。
若要修改数据库连接配置，请修改 application.properties 文件。
该项目使用了Lombok来简化代码编写，请确保IDE已安装Lombok插件。
