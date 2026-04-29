import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TwitchIngestionStatus } from './TwitchIngestionStatus'

describe('TwitchIngestionStatus', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders disabled state from the status endpoint', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        enabled: false,
        state: 'DISABLED',
        channels: [],
        lastMessageAt: 0,
        lastError: null,
        reconnectAttempts: 0,
      }),
    } as Response)

    render(<TwitchIngestionStatus />)

    await waitFor(() => expect(screen.getByText('Twitch: disabled')).toBeInTheDocument())
  })

  it('renders connected channels', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        enabled: true,
        state: 'CONNECTED',
        channels: ['testchannel'],
        lastMessageAt: 1710000000000,
        lastError: null,
        reconnectAttempts: 0,
      }),
    } as Response)

    render(<TwitchIngestionStatus />)

    await waitFor(() => expect(screen.getByText('Twitch: connected @testchannel')).toBeInTheDocument())
  })
})
