import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSubscription } from '@apollo/client/react'
import { LiveChat } from './LiveChat'

type SubscriptionOptions = {
  variables?: { streamer?: string }
  skip?: boolean
  onData?: (value: {
    data?: {
      data?: {
        onChatMessage?: {
          eventId: string
          streamer: string
          user: string
          message: string
          timestamp: number
        }
      }
    }
  }) => void
}

vi.mock('@apollo/client/react', () => ({
  useSubscription: vi.fn(),
}))

const useSubscriptionMock = vi.mocked(useSubscription)

describe('LiveChat', () => {
  let lastOptions: SubscriptionOptions | undefined

  beforeEach(() => {
    lastOptions = undefined
    useSubscriptionMock.mockImplementation((_, options) => {
      lastOptions = options as SubscriptionOptions
      return { error: undefined } as never
    })
  })

  it('renders the initial disconnected state', () => {
    render(<LiveChat />)

    expect(screen.getByText('Connect to start receiving events.')).toBeInTheDocument()
    expect(screen.getByText(/Status:/)).toHaveTextContent('Status: disconnected')
  })

  it('renders the listening empty state after connecting', () => {
    render(<LiveChat />)

    fireEvent.click(screen.getByRole('button', { name: 'Connect' }))

    expect(screen.getByText('No messages yet — ingest some events.')).toBeInTheDocument()
    expect(screen.getByText(/Status:/)).toHaveTextContent('listening (streamer=test)')
    expect(lastOptions?.skip).toBe(false)
    expect(lastOptions?.variables?.streamer).toBe('test')
  })

  it('renders an incoming subscription event', () => {
    render(<LiveChat />)

    fireEvent.click(screen.getByRole('button', { name: 'Connect' }))

    act(() => {
      lastOptions?.onData?.({
        data: {
          data: {
            onChatMessage: {
              eventId: 'evt-123',
              streamer: 'test',
              user: 'u1',
              message: 'hello from test',
              timestamp: 1710000000000,
            },
          },
        },
      })
    })

    expect(screen.getByText('u1')).toBeInTheDocument()
    expect(screen.getByText('hello from test')).toBeInTheDocument()
    expect(screen.getByText(/eventId=evt-123/)).toBeInTheDocument()
  })
})
