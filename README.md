<p align="center">
  <img src="https://github.com/user-attachments/assets/fd25272e-2328-4a58-92f6-2b88b1deffcf" width="420" alt="RACE 로고">
</p>

<h1 align="center">🏁 RACE</h1>

<p align="center">
  <strong>누구나 직접 사고팔 수 있는 실시간 중고차 경매 플랫폼</strong><br>
  방문 평가부터 실시간 경매, 낙찰 이후 거래까지 하나의 흐름으로 이어집니다.
</p>

<p align="center">
  <a href="https://www.f1race.site"><strong>🌐 서비스 바로가기</strong></a>
  ·
  <a href="https://github.com/softeerbootcamp-8th/WEB-Team1-F1/wiki"><strong>📖 팀 위키</strong></a>
  ·
  <a href="#️-시스템-아키텍처"><strong>🏗️ 시스템 아키텍처</strong></a>
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
- [🔧 Tech Stack](#-tech-stack)
- [🏗️ 시스템 아키텍처](#-시스템-아키텍처)
- [👥 팀원 소개](#-팀원-소개)

## 🚀 프로젝트 개요

- 딜러를 거쳐야만하고, 그 마진이 고스란히 딜러에게 귀속되던 중고차 시장을 대상으로 합니다.
- 일반인과 딜러가 함께 참여하는 실시간 경매로, 시장이 차량의 적정 가치를 직접 발견하게 합니다.
- 딜러 마진 없이 개인 간 거래(C2C)가 가능해, 판매자는 더 비싸게 팔고 구매자는 더 싸게 살 수 있습니다.

## 🗺️ 서비스 전체 흐름

- 차량 시세 조회부터 방문 진단, 실시간 경매, 낙찰 후 거래 4단계까지 이어지는 전체 흐름입니다.
- 평가 반려 시 방문평가 재신청으로, 유찰 시 경매 재출품으로 되돌아갑니다.

![RACE 서비스 전체 플로우](https://github.com/user-attachments/assets/9bae55a2-4b83-44c1-8207-54ed5c2071e8)

## ✨ 주요 기능

- 실시간 경매
    - 마감 전까지 실시간으로 입찰하며 최고가 갱신을 바로 확인할 수 있습니다.
    - 소프트 클로즈 방식을 적용하여 마감 직전에 입찰이 들어오면 마감 시각이 연장되어, 실시간 라이브 경매의 치열한 경쟁을 웹에서 구현합니다.
    - 마지막 30초동안 더 이상 입찰이 없으면 최고 입찰자가 낙찰됩니다.

## 🔧 Tech Stack

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

| <img src="https://github.com/user-attachments/assets/f9abd491-6427-41d6-a36c-0dd3e91adafa" width="100"> | <img src="https://github.com/tukjw.png" width="100"> | <img src="https://github.com/user-attachments/assets/cc09b641-801f-46a0-a2fa-e9de58ebdd4c" width="100"> | <img src="https://github.com/mookkae.png" width="100"> |
| --- | --- | --- | --- |
| 김어진 | 김재완 | 박태은 | 정동현 |
| BE | BE | BE | BE |
