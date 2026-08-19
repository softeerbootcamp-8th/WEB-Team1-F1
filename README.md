<p align="center">
  <img src="https://github.com/user-attachments/assets/fd25272e-2328-4a58-92f6-2b88b1deffcf" width="420" alt="RACE 로고">
</p>

<h1 align="center">🏁 RACE</h1>

<p align="center">
  <strong>누구나 사고팔 수 있는 실시간 중고차 경매 플랫폼</strong><br>
  방문 평가부터 실시간 경매, 낙찰 이후 거래까지 하나의 흐름으로 이어집니다.
</p>

<p align="center">
  <a href="https://www.f1race.site"><strong>🌐 서비스 바로가기</strong></a>
  ·
  <a href="https://github.com/softeerbootcamp-8th/WEB-Team1-F1/wiki"><strong>📖 팀 위키</strong></a>
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/35d4eca8-5120-4858-8fee-e7a0a535d371" width="100%" alt="RACE 서비스 메인 화면">
</p>

<p align="center">
  <sub>소프티어 부트캠프 8기 · WEB Team1 (F1)</sub>
</p>

---

## 📚 목차

- [🚀 프로젝트 개요](#-프로젝트-개요)
- [🗺️ 서비스 전체 흐름](#-서비스-전체-흐름)
- [✨ 주요 기능](#-주요-기능)
- [🔧 기술 스택](#-기술-스택)
- [🏗️ 시스템 아키텍처](#-시스템-아키텍처)
- [👥 팀원 소개](#-팀원-소개)

## 🚀 프로젝트 개요

- 누구나 중고차를 직접 사고팔 수 있는 실시간 경매 플랫폼입니다. 개인 판매자와 구매자, 중고차 딜러가 같은 경매에 참여합니다.
- **합리적인 가격** — 딜러의 유통 마진을 없애 판매자는 딜러 매입가보다 더 받고, 구매자는 딜러 소매가보다 더 싸게 삽니다.
- **평가사가 검증한 신뢰성** — 평가사가 직접 차량을 방문해 사진과 진단 결과를 기록하고, 승인된 차량만 경매에 오릅니다.
- **입찰 현황을 공개하는 투명성** — 현재가와 최근 입찰 내역, 참여 인원이 경매방을 보고 있는 모든 이용자에게 실시간으로 동일하게 보입니다.

## 🗺️ 서비스 전체 흐름

- 차량 시세 조회부터 방문 진단, 실시간 경매, 낙찰 후 거래 4단계까지 이어지는 전체 흐름입니다.
- 평가 반려 시 방문평가 재신청으로, 유찰 시 경매 재출품으로 되돌아갑니다.

![RACE 서비스 전체 플로우](https://github.com/user-attachments/assets/9bae55a2-4b83-44c1-8207-54ed5c2071e8)

## ✨ 주요 기능

### 차량 방문 평가

  ![차량 방문 평가](https://github.com/user-attachments/assets/42188075-7a8c-423a-9327-d15043081363)
  
- 판매자가 차량이 있는 장소와 원하는 방문 일정을 골라 평가를 신청하면, 평가사가 직접 방문해 사진과 진단 결과를 기록하여 승인된 차량만 경매에 오릅니다.
 
- **사용자 알림** — 방문 평가 결과, 경매 시작, 최고 입찰자 변경, 낙찰과 유찰, 거래 단계 전이를 실시간 알림으로 안내하고, 아직 시작하지 않은 경매는 시작 알림을 미리 신청해 둘 수 있습니다.
- **낙찰 이후 거래 진행** — 낙찰과 동시에 거래가 자동으로 생성되고, 구매 확정 → 판매 서류와 탁송 일정 등록 → 인도 일정 확정 순으로 구매자와 판매자가 번갈아 단계를 밟습니다.

### 실시간 경매

![실시간 경매](https://github.com/user-attachments/assets/19438b49-5d0c-4694-a6ae-2db9f0eb3fb5)

- 가격대별 최소 상승 단위 위로만 입찰할 수 있고, 입찰이 성사되면 현재가와 최근 입찰 내역, 참여 인원, 남은 시간이 그 경매방을 보고 있는 모든 이용자의 화면에 함께 반영됩니다.
- 마감 직전 입찰이 마감을 미루는 **소프트 클로즈**를 적용해, 마감까지 30초가 남지 않은 시점에 입찰이 들어오면 마감 시각이 그 입찰 시점부터 다시 30초로 늘어납니다.

## 🔧 기술 스택

| 영역 | 스택 |
| --- | --- |
| **Backend** | ![Java](https://img.shields.io/badge/Java%2021-007396?style=for-the-badge&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot%204.1.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white) ![Swagger](https://img.shields.io/badge/Swagger%20UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) |
| **Database** | ![MySQL](https://img.shields.io/badge/MySQL%208.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-FF4438?style=for-the-badge&logo=redis&logoColor=white) |
| **Frontend** | ![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB) ![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white) ![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white) ![pnpm](https://img.shields.io/badge/pnpm-F69220?style=for-the-badge&logo=pnpm&logoColor=white) |
| **Test** | ![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?style=for-the-badge&logo=junit5&logoColor=white) ![Mockito](https://img.shields.io/badge/Mockito-78A641?style=for-the-badge) ![Testcontainers](https://img.shields.io/badge/Testcontainers-291A38?style=for-the-badge&logo=testcontainers&logoColor=white) |
| **Infra** | ![EC2](https://img.shields.io/badge/EC2-FF9900?style=for-the-badge) ![S3](https://img.shields.io/badge/S3-569A31?style=for-the-badge) ![CloudFront](https://img.shields.io/badge/CloudFront-8C4FFF?style=for-the-badge) ![VPC](https://img.shields.io/badge/VPC-8C4FFF?style=for-the-badge) ![Nginx](https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white) |
| **CI/CD** | ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white) ![GHCR](https://img.shields.io/badge/GHCR-181717?style=for-the-badge&logo=github&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white) |

## 🏗️ 시스템 아키텍처

![RACE 시스템 아키텍처](https://github.com/user-attachments/assets/7befdd14-e1c3-4dfc-96af-fa9c3f221b2f)

## 👥 팀원 소개

<div align="center">

| <img src="https://github.com/user-attachments/assets/f9abd491-6427-41d6-a36c-0dd3e91adafa" width="150"> | <img src="https://github.com/user-attachments/assets/7aa5297a-cc04-495a-bbde-36086cee4579" width="150"> | <img src="https://github.com/user-attachments/assets/cc09b641-801f-46a0-a2fa-e9de58ebdd4c" width="150"> | <img src="https://github.com/user-attachments/assets/6013009d-912f-4529-b136-a60c8bbf5ca1" width="150"> |
| --- | --- | --- | --- |
| 김어진 | 김재완 | 박태은 | 정동현 |
| BE | BE | BE | BE |

</div>
