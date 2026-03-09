# PACS上传功能使用说明

## 功能概述
已成功实现头部出血检测质控的PACS上传功能，用户可以通过查询条件搜索PACS记录并选择进行质控分析。

## 部署步骤

### 1. 数据库初始化
执行SQL脚本创建PACS缓存表并插入测试数据：
```bash
mysql -u root -p medical_qc_sys < pacs_study_cache.sql
```

### 2. 启动后端服务
```bash
cd medical-qc-backend
mvn spring-boot:run
```

### 3. 启动前端服务
```bash
cd medical-qc-frontend
npm run dev
```

## 使用流程

1. 登录系统后进入"头部出血检测"页面
2. 点击"PACS系统检索"按钮
3. 在弹出的对话框中输入查询条件：
   - 患者姓名（支持模糊查询）
   - 患者ID（精确匹配）
   - 检查号（精确匹配）
   - 检查日期范围
4. 点击"查询"按钮查看结果
5. 点击选择某条检查记录
6. 系统自动填充患者信息到上传表单
7. 确认信息后提交质控任务

## 测试数据

系统已预置5条测试数据：
- 患者：张三、李四、王五、赵六、孙七
- 检查日期：2026-03-04 至 2026-03-08
- 检查号：ACC001 至 ACC005

## 扩展说明

当前为模拟实现，后续对接真实PACS系统时，只需修改 `PacsServiceImpl.java` 中的查询逻辑，前端无需改动。

支持的对接方式：
- DICOM C-FIND/C-MOVE
- DICOMweb (QIDO-RS/WADO-RS)
- PACS厂商SDK

## 其他质控项扩展

其他四个质控项可复用相同的PACS查询组件，参考Hemorrhage.vue的集成方式即可。
