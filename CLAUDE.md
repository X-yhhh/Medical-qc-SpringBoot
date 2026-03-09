# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Medical QC System - A medical quality control platform with hemorrhage detection and multi-modal quality checks. Frontend-backend separated architecture with Python ML model integration.

**Tech Stack:**
- Backend: Spring Boot 3.2.2 + MyBatis-Plus + MySQL 8.0 + Redis + ActiveMQ
- Frontend: Vue 3 + Element Plus + Vite
- ML Model: PyTorch (WebSocket communication via model server)
- Java Version: 17 (compiled), running on 25

## Architecture

### Three-Tier System
1. **Frontend** (`medical-qc-frontend/`): Vue 3 SPA with role-based routing (doctor/admin)
2. **Backend** (`medical-qc-backend/`): Spring Boot REST API with layered architecture
3. **ML Model** (`medical-qc-backend/python_model/`): PyTorch model server (WebSocket on port 8765)

### Backend Layer Structure
- **Controller**: HTTP endpoints, request validation
- **Service**: Interface definitions only
- **ServiceImpl**: Business logic implementation
- **Mapper**: SQL queries only (MyBatis-Plus)
- **Entity**: Database table mappings
- **Messaging**: ActiveMQ message producers/consumers
- **Support**: Lifecycle management (Python model auto-start)

### Key Data Flow
- Hemorrhage detection: Frontend → Backend → Python Model (WebSocket) → Backend → Redis/MySQL → Frontend
- Session management: Redis-backed Spring Session
- Async tasks: ActiveMQ queues (`qc.hemorrhage.issue.sync`, `qc.mock.quality.task`)

## Database Schema

**Core Tables:**
- `users`: User authentication, roles (admin/doctor)
- `hemorrhage_records`: Hemorrhage detection history with QC status
- `qc_issue_records`: Unified issue tracking across all QC types
- `qc_issue_handle_logs`: Issue status change audit trail
- `head_qc_records`, `chest_non_contrast_qc_records`, `chest_contrast_qc_records`, `coronary_cta_qc_records`: Individual QC type histories

**Relationships:**
- `users (1) -> (N) hemorrhage_records`
- `hemorrhage_records (1) -> (0..1) qc_issue_records` (via `source_type='hemorrhage'` + `source_record_id`)
- `qc_issue_records (1) -> (N) qc_issue_handle_logs`

## Development Commands

### Backend (Spring Boot)
```bash
cd medical-qc-backend
mvn clean install          # Build
mvn spring-boot:run        # Run (port 8080)
mvn test                   # Run tests
```

### Frontend (Vue 3)
```bash
cd medical-qc-frontend
npm install                # Install dependencies
npm run dev                # Dev server (Vite)
npm run build              # Production build
npm run lint               # ESLint with auto-fix
npm run type-check         # TypeScript type checking
```

### Python Model Server
```bash
# Activate virtual environment first
cd medical-qc-backend/python_model
python model_server.py     # Start WebSocket server on port 8765
```

**Note:** Backend auto-starts Python model if `python.model.autostart=true` in `application.properties`.

### Required Services
- **MySQL**: Port 3306, database `medical_qc_sys` (use `init.sql` to initialize)
- **Redis**: Port 6379 (session storage)
- **ActiveMQ**: Port 61616 (auto-started by backend if configured)

## Critical Rules

1. **Model Integration**: Hemorrhage detection MUST call the existing PyTorch model in `medical-qc-backend/python_model/`. Never mock or randomly generate results.

2. **Code Comments**: All code must have comprehensive Chinese comments explaining purpose, parameters, return values, and exceptions.

3. **Layered Architecture**: Strictly follow Controller → Service (interface) → ServiceImpl (logic) → Mapper (SQL) → Entity pattern.

4. **No Feature Creep**: This is a 1:1 refactor of an existing system. Do not add features or modify functionality without explicit request.

5. **WebSocket Communication**: Python model server uses WebSocket protocol. Backend connects via `python.model_server.url=ws://localhost:8765`.

## File Paths

- Backend config: `medical-qc-backend/src/main/resources/application.properties`
- Frontend router: `medical-qc-frontend/src/router/index.js`
- API definitions: `medical-qc-frontend/src/api/`
- Database init: `init.sql` (root directory)
- Python models: `medical-qc-backend/python_model/` and `predict_hemorrhage.py` (root)

## Authentication & Authorization

- Session-based auth with Redis storage
- Roles: `admin` (role_id=1), `doctor` (role_id=2)
- Frontend route guards check `meta.requiresAuth` and `meta.roles`
- Backend uses `@AuthRole` annotation for endpoint protection

## Current Implementation Status

**Fully Implemented:**
- User authentication/registration
- Hemorrhage detection with real ML model
- Dashboard with real-time stats
- Issue tracking and status management
- Redis session persistence

**Mock APIs (awaiting real implementation):**
- Head QC, Chest Non-Contrast QC, Chest Contrast QC, Coronary CTA QC (tables exist, detection logic pending)
