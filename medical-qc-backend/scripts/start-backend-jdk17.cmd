@echo off
setlocal

cd /d "%~dp0.."

set "JAVA_HOME=D:\JDK17"
set "JDK_HOME=D:\JDK17"
set "MAVEN_HOME=E:\Maven\apache-maven-3.9.13"
set "M2_HOME=E:\Maven\apache-maven-3.9.13"
set "CATALINA_HOME=D:\Apache Tomcat\apache-tomcat-10.1.52"
set "CATALINA_BASE=D:\Apache Tomcat\apache-tomcat-10.1.52"
set "TOMCAT_HOME=D:\Apache Tomcat\apache-tomcat-10.1.52"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%CATALINA_HOME%\bin;%PATH%"

call mvn spring-boot:run >> "%CD%\backend-runtime.log" 2>&1
