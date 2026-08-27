import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useQuery } from '@apollo/client/react'
import { Health } from './Health'

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
}))

const useQueryMock = vi.mocked(useQuery)

describe('Health', () => {
  it('renders the success state', () => {
    useQueryMock.mockReturnValue({
      data: { health: 'ok' },
      loading: false,
      error: undefined,
    } as never)

    render(<Health />)

    expect(screen.getByText('Health: ok')).toBeInTheDocument()
  })
})
