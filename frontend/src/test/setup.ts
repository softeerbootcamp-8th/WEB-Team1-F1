import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// RTL 은 전역 afterEach 가 있을 때만 스스로 정리한다. globals 를 켜지 않으므로 여기서 부른다
afterEach(cleanup)
