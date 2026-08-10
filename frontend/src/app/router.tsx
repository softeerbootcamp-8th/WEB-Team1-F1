import { Link, Route, Routes } from 'react-router-dom'

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
import { DealsPage } from '@/features/deals/pages/deals-page'
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
        <Route path="/mypage" element={<MyPage />} />
        <Route path="/deals" element={<DealsPage />} />
        {/* 알림 딥링크의 목적지. 주소를 바꾸면 이미 쌓인 알림까지 소급해 바뀐다 */}
        <Route path="/deals/:dealId" element={<DealDetailPage />} />
        <Route path="/mypage/evaluations/:evaluationId" element={<MyRequestDetailPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
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
