/**
 * Paces dispatch of `sendOne` calls at roughly `requestsPerSecond`, never exceeding `concurrency`
 * in flight at once. A tick that lands while already at the concurrency cap simply dispatches
 * nothing that tick — the simulator models *offered* load, so the configured rate is the target
 * a rate limiter should be measured against, not a throughput guarantee.
 */
export interface BurstConfig {
  /** Number.POSITIVE_INFINITY for "run until stopped". */
  totalCount: number
  requestsPerSecond: number
  concurrency: number
}

export function runBurst(
  config: BurstConfig,
  sendOne: (sequence: number) => Promise<void>,
  signal: AbortSignal,
): Promise<void> {
  return new Promise((resolve) => {
    let dispatched = 0
    let inFlight = 0
    const intervalMs = config.requestsPerSecond > 0 ? 1000 / config.requestsPerSecond : 0

    const settleIfDone = () => {
      if ((signal.aborted || dispatched >= config.totalCount) && inFlight === 0) {
        clearInterval(timer)
        resolve()
      }
    }

    const dispatchOne = () => {
      if (signal.aborted || dispatched >= config.totalCount || inFlight >= config.concurrency) return
      dispatched += 1
      inFlight += 1
      const sequence = dispatched
      sendOne(sequence).finally(() => {
        inFlight -= 1
        settleIfDone()
      })
    }

    const timer = setInterval(() => {
      if (signal.aborted || dispatched >= config.totalCount) {
        settleIfDone()
        return
      }
      dispatchOne()
    }, Math.max(intervalMs, 1))

    signal.addEventListener('abort', settleIfDone)

    dispatchOne() // don't wait a full interval for the first request
  })
}
