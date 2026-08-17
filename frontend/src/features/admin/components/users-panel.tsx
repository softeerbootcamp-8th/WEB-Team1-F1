import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, LoaderCircle, Search } from 'lucide-react'
import { toast } from 'sonner'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/common/empty-state'
import { ROLE_LABEL } from '@/features/auth/auth-context'
import { getErrorMessage } from '@/lib/axios'
import { formatPhone } from '@/lib/format'
import type { UserRole } from '@/types/domain'
import { activateUser, fetchUserDetail, fetchUsers, suspendUser } from '../api'
import { formatServerDateTime } from '../format'
import { ADMIN_USERS_QUERY_KEY, adminUserDetailQueryKey, adminUsersQueryKey } from '../query-keys'
import {
  MAX_SUSPEND_REASON_LENGTH,
  USER_STATUS_LABEL,
  type UserSearchCondition,
  type UserStatus,
  type UserSummary,
} from '../types'

/** 필터의 "전체". null 을 Select·Tabs 의 value 로 쓸 수 없어 이 문자열로 대신한다 */
const ALL = 'ALL'

const STATUS_TABS: (UserStatus | typeof ALL)[] = [ALL, 'ACTIVE', 'SUSPENDED']
const ROLE_OPTIONS: UserRole[] = ['GENERAL', 'DEALER', 'EVALUATOR', 'ADMIN']

const FIRST_PAGE: UserSearchCondition = { keyword: '', role: null, status: null, page: 0 }

/**
 * 회원 관리. 검색 → 확인 → 조치가 한 자리에서 끝나도록 상세를 다이얼로그로 띄운다.
 * 별도 상세 화면을 두면 정지 한 번에 화면을 두 번 옮겨야 하고, 돌아왔을 때 검색 조건이 날아간다.
 */
export function UsersPanel() {
  // 입력 중인 검색어와 조회에 걸린 검색어를 나눈다. 이 검색은 서버에서 인덱스를 타지 못해
  // 전체를 훑으므로, 타이핑마다 보내지 않고 제출한 순간에만 보낸다
  const [keywordInput, setKeywordInput] = useState('')
  const [condition, setCondition] = useState<UserSearchCondition>(FIRST_PAGE)
  const [openUserId, setOpenUserId] = useState<number | null>(null)

  const usersQuery = useQuery({
    queryKey: adminUsersQueryKey(condition),
    queryFn: () => fetchUsers(condition),
  })

  /**
   * 조건을 바꿀 때는 첫 페이지로 돌아간다. 3페이지를 보던 중 필터를 좁히면 그 조건에는 3페이지가
   * 없어서, 페이지를 물고 가면 빈 목록이 나오고 관리자는 결과가 없다고 읽는다.
   */
  const narrow = (patch: Partial<Omit<UserSearchCondition, 'page'>>) =>
    setCondition((prev) => ({ ...prev, ...patch, page: 0 }))

  const users = usersQuery.data?.users ?? []
  const totalPages = usersQuery.data?.totalPages ?? 0
  const totalUsers = usersQuery.data?.totalUsers ?? 0

  return (
    <div>
      <Card>
        <CardHeader className="gap-4">
          <CardTitle>
            회원 목록
            <span className="text-muted-foreground ml-2 text-sm font-normal">
              총 {totalUsers}명
            </span>
          </CardTitle>

          <form
            className="flex flex-wrap gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              narrow({ keyword: keywordInput })
            }}
          >
            <Input
              className="max-w-xs"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              placeholder="아이디 · 이름 · 연락처"
              aria-label="회원 검색"
            />
            <Button type="submit" variant="outline">
              <Search />
              검색
            </Button>
            <Select
              value={condition.role ?? ALL}
              onValueChange={(value) =>
                narrow({ role: value === ALL ? null : (value as UserRole) })
              }
            >
              <SelectTrigger className="w-36" aria-label="역할 선택">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>역할 전체</SelectItem>
                {ROLE_OPTIONS.map((role) => (
                  <SelectItem key={role} value={role}>
                    {ROLE_LABEL[role]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </form>

          <Tabs
            value={condition.status ?? ALL}
            onValueChange={(value) =>
              narrow({ status: value === ALL ? null : (value as UserStatus) })
            }
          >
            <TabsList>
              {STATUS_TABS.map((tab) => (
                <TabsTrigger key={tab} value={tab}>
                  {tab === ALL ? '전체' : USER_STATUS_LABEL[tab]}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </CardHeader>

        <CardContent>
          {usersQuery.isLoading ? (
            <div className="space-y-3" aria-label="목록 불러오는 중">
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-20 w-full" />
            </div>
          ) : usersQuery.isError ? (
            <EmptyState
              title="회원 목록을 불러오지 못했습니다"
              description={getErrorMessage(usersQuery.error, '잠시 후 다시 시도해 주세요.')}
            />
          ) : users.length === 0 ? (
            <EmptyState title="조건에 맞는 회원이 없습니다" />
          ) : (
            <ul className="space-y-3">
              {users.map((user) => (
                <li key={user.id}>
                  <UserRow user={user} onOpen={() => setOpenUserId(user.id)} />
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {totalPages > 1 && (
        <nav className="mt-6 flex items-center justify-center gap-4" aria-label="페이지 이동">
          <Button
            variant="outline"
            size="sm"
            disabled={condition.page === 0 || usersQuery.isFetching}
            onClick={() => setCondition((prev) => ({ ...prev, page: prev.page - 1 }))}
          >
            <ChevronLeft />
            이전
          </Button>
          {/* 서버는 0부터 세고 사람은 1부터 센다. 그 변환은 이 표기 한 곳에서만 한다 */}
          <span className="text-muted-foreground text-sm">
            {condition.page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={condition.page >= totalPages - 1 || usersQuery.isFetching}
            onClick={() => setCondition((prev) => ({ ...prev, page: prev.page + 1 }))}
          >
            다음
            <ChevronRight />
          </Button>
        </nav>
      )}

      <UserDetailDialog userId={openUserId} onClose={() => setOpenUserId(null)} />
    </div>
  )
}

function UserRow({ user, onOpen }: { user: UserSummary; onOpen: () => void }) {
  const suspended = user.status === 'SUSPENDED'

  return (
    <button
      type="button"
      onClick={onOpen}
      className="border-border hover:bg-accent/50 flex w-full items-center gap-4 rounded-xl border bg-white p-5 text-left transition-colors"
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-semibold">{user.realName}</span>
          <span className="text-muted-foreground text-sm">@{user.username}</span>
          <Badge variant="outline">{ROLE_LABEL[user.role]}</Badge>
          {/* 정지만 눈에 띄어야 한다. 이용 중이 기본값이라 그 배지는 목록을 시끄럽게만 만든다 */}
          {suspended && <Badge variant="destructive">{USER_STATUS_LABEL.SUSPENDED}</Badge>}
        </div>
        <p className="text-muted-foreground mt-1 text-sm">
          {formatServerDateTime(user.joinedAt)} 가입
        </p>
      </div>
    </button>
  )
}

/**
 * 회원 상세와 이용정지 조치. 한 다이얼로그가 두 모습을 갖는다 — 정보를 보는 모습과 정지 사유를
 * 받는 모습. 사유 입력을 다이얼로그 안의 다이얼로그로 띄우면 포커스 관리가 겹쳐 접근성이 깨진다.
 */
function UserDetailDialog({ userId, onClose }: { userId: number | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [askingReason, setAskingReason] = useState(false)

  const detailQuery = useQuery({
    queryKey: adminUserDetailQueryKey(userId ?? 0),
    queryFn: () => fetchUserDetail(userId as number),
    enabled: userId !== null,
  })

  const close = () => {
    onClose()
    setAskingReason(false)
    setReason('')
  }

  /** 조치 뒤에는 목록도 상세도 낡는다. 그 회원이 상태별 목록 사이를 옮겨 가기 때문이다 */
  const invalidateAfterDecision = () =>
    void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY })

  const suspendMutation = useMutation({
    mutationFn: () => {
      const trimmed = reason.trim()
      if (!trimmed) throw new Error('정지 사유를 입력해 주세요.')
      if (trimmed.length > MAX_SUSPEND_REASON_LENGTH) {
        throw new Error(`정지 사유는 ${MAX_SUSPEND_REASON_LENGTH}자까지 입력할 수 있습니다.`)
      }
      return suspendUser(userId as number, trimmed)
    },
    onSuccess: () => {
      // 정지는 그 회원의 세션까지 끊는다. 관리자가 이걸 알아야 "갑자기 로그아웃됐다"는 문의에 답할 수 있다
      toast.success('이용을 정지했습니다. 해당 회원은 즉시 로그아웃되고 다시 로그인할 수 없습니다')
      invalidateAfterDecision()
      close()
    },
    onError: (error) => {
      // 사유 검증은 여기서 막혀 서버까지 가지 않는다. 그때는 axios 에러가 아니라 우리가 던진 것이다
      const localMessage = error instanceof Error && !('response' in error) ? error.message : null
      toast.error(localMessage ?? getErrorMessage(error, '이용을 정지하지 못했습니다'))
    },
  })

  const activateMutation = useMutation({
    mutationFn: () => activateUser(userId as number),
    onSuccess: () => {
      toast.success('이용 정지를 해제했습니다. 해당 회원은 다시 로그인할 수 있습니다')
      invalidateAfterDecision()
      close()
    },
    onError: (error) => toast.error(getErrorMessage(error, '정지를 해제하지 못했습니다')),
  })

  const detail = detailQuery.data
  // 관리자·평가사는 서버가 정지 대상에서 빼 둔다. 누를 수 있게 두면 400 을 받으려고 누르는 버튼이 된다
  const suspendable = detail?.role === 'GENERAL' || detail?.role === 'DEALER'
  const isDeciding = suspendMutation.isPending || activateMutation.isPending

  return (
    <Dialog open={userId !== null} onOpenChange={(open) => !open && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{askingReason ? '이용 정지' : '회원 정보'}</DialogTitle>
          <DialogDescription>
            {askingReason
              ? '사유는 관리자만 보는 기록입니다. 정지하면 그 회원의 세션이 즉시 끊깁니다.'
              : '이 회원의 기본 정보와 이용 상태입니다.'}
          </DialogDescription>
        </DialogHeader>

        {detailQuery.isLoading ? (
          <div className="flex justify-center py-10">
            <LoaderCircle className="size-6 animate-spin" aria-label="회원 정보 불러오는 중" />
          </div>
        ) : detailQuery.isError || !detail ? (
          <EmptyState
            title="회원 정보를 불러오지 못했습니다"
            description={getErrorMessage(detailQuery.error, '존재하지 않는 회원입니다.')}
          />
        ) : askingReason ? (
          <div className="space-y-2">
            <Label htmlFor="suspend-reason">정지 사유</Label>
            <textarea
              id="suspend-reason"
              className="border-input placeholder:text-muted-foreground focus-visible:ring-ring min-h-28 w-full rounded-md border bg-transparent px-3 py-2 text-sm focus-visible:ring-1 focus-visible:outline-none"
              maxLength={MAX_SUSPEND_REASON_LENGTH}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="예) 허위 매물을 반복 등록했습니다."
            />
            <p className="text-muted-foreground text-right text-xs">
              {reason.length} / {MAX_SUSPEND_REASON_LENGTH}
            </p>
          </div>
        ) : (
          <div className="space-y-3 text-sm">
            <Field label="아이디" value={detail.username} />
            <Field label="이름" value={detail.realName} />
            <Field label="이메일" value={detail.email} />
            <Field label="휴대전화" value={formatPhone(detail.phone)} />
            <Field label="역할" value={ROLE_LABEL[detail.role]} />
            <Field label="이용 상태" value={USER_STATUS_LABEL[detail.status]} />
            <Field label="가입" value={formatServerDateTime(detail.joinedAt)} />
            {detail.suspendReason && <Field label="정지 사유" value={detail.suspendReason} />}
          </div>
        )}

        <DialogFooter>
          {askingReason ? (
            <>
              <Button variant="outline" onClick={() => setAskingReason(false)} disabled={isDeciding}>
                취소
              </Button>
              <Button
                variant="destructive"
                onClick={() => suspendMutation.mutate()}
                disabled={isDeciding}
              >
                정지하기
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={close}>
                닫기
              </Button>
              {detail?.status === 'SUSPENDED' ? (
                <Button onClick={() => activateMutation.mutate()} disabled={isDeciding}>
                  정지 해제
                </Button>
              ) : (
                suspendable && (
                  <Button variant="destructive" onClick={() => setAskingReason(true)}>
                    이용 정지
                  </Button>
                )
              )}
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3">
      <span className="text-muted-foreground w-20 shrink-0">{label}</span>
      <span className="min-w-0 break-words">{value}</span>
    </div>
  )
}
