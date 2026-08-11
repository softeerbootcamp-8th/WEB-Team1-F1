import { Link, Navigate, Route, Routes, useParams } from 'react-router-dom'

import { AppLayout } from '@/components/layout/app-layout'
import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { HomePage } from '@/features/auctions/pages/home-page'
import { AuctionsPage } from '@/features/auctions/pages/auctions-page'
import { AuctionRoomPage } from '@/features/auction-room/pages/auction-room-page'
import { SellPage } from '@/features/sell/pages/sell-page'
import { EvaluatorConnectionPage } from '@/features/sell/pages/evaluator-connection-page'
import { AuctionPostPage } from '@/features/sell/pages/auction-post-page'
import { SellResultPage } from '@/features/sell/pages/sell-result-page'
import { PriceQuotePage } from '@/features/quote/pages/price-quote-page'
import { QuoteResultPage } from '@/features/quote/pages/quote-result-page'
import { MyPage } from '@/features/deals/pages/mypage'
import { DealDetailPage } from '@/features/deals/pages/deal-detail-page'
import { LoginPage } from '@/features/auth/pages/login-page'
import { SignupPage } from '@/features/auth/pages/signup-page'
import { AssignableEvaluationsPage } from '@/features/evaluations/pages/assignable-evaluations-page'
import { MyAssignmentsPage } from '@/features/evaluations/pages/my-assignments-page'
import { EvaluationResultPage } from '@/features/evaluations/pages/evaluation-result-page'
import { MyRequestDetailPage } from '@/features/evaluations/pages/my-request-detail-page'

export function AppRouter() {
  return (
    <Routes>
      {/* 인증 화면은 전용 풀스크린 레이아웃 */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />

      {/* 기본 레이아웃(헤더/푸터) */}
      <Route element={<AppLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/quote" element={<PriceQuotePage />} />
        <Route path="/quote/result" element={<QuoteResultPage />} />
        <Route path="/auctions" element={<AuctionsPage />} />
        <Route path="/auctions/:id" element={<AuctionRoomPage />} />
        <Route path="/sell" element={<SellPage />} />
        <Route path="/sell/evaluator" element={<EvaluatorConnectionPage />} />
        <Route path="/sell/auction-post" element={<AuctionPostPage />} />
        <Route path="/sell/result" element={<SellResultPage />} />
        <Route path="/evaluations/assignable" element={<AssignableEvaluationsPage />} />
        <Route path="/evaluations/my" element={<MyAssignmentsPage />} />
        <Route path="/evaluations/:evaluationId/result" element={<EvaluationResultPage />} />
        {/*
          * 마이페이지의 탭이 곧 경로다. 상세는 주소만 접두사를 공유하고 컴포넌트는 독립이라,
          * 마이페이지 화면 구조가 바뀌어도 딥링크가 함께 흔들리지 않는다.
          */}
        <Route path="/mypage" element={<MyPage />} />
        <Route path="/mypage/evaluations" element={<MyPage />} />
        <Route path="/mypage/deals" element={<MyPage />} />
        <Route path="/mypage/auctions" element={<MyPage />} />
        <Route path="/mypage/evaluations/:evaluationId" element={<MyRequestDetailPage />} />
        {/* 알림 딥링크의 목적지. 서버 NotificationType 의 링크와 한 쌍이다 */}
        <Route path="/mypage/deals/:dealId" element={<DealDetailPage />} />

        {/* 옛 주소. 어딘가에 복사돼 있을 수 있어 남긴다 */}
        <Route path="/deals" element={<Navigate to="/mypage/deals" replace />} />
        <Route path="/deals/:dealId" element={<LegacyDealRedirect />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

/** 옛 거래 상세 주소를 새 자리로 넘긴다. 번호를 그대로 물고 가야 알림이 가리키던 거래에 닿는다 */
function LegacyDealRedirect() {
  const { dealId } = useParams()

  return <Navigate to={`/mypage/deals/${dealId}`} replace />
}

function NotFound() {
  return (
    <main aria-label="페이지 없음" className="mx-auto max-w-3xl px-6 py-24">
      <EmptyState
        title="페이지를 찾을 수 없습니다"
        description="주소를 다시 확인해 주세요."
        action={
          <Button asChild variant="outline">
            <Link to="/">홈으로</Link>
          </Button>
        }
      />
    </main>
  )
}
