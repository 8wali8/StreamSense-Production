/** One sentence per reason the gateway can send the browser back with (`/?signin=<reason>`). */
export function describeSignInFailure(reason: string | null | undefined): string | null {
  switch (reason) {
    case "denied":
      return "Twitch sign-in was cancelled. Nothing was changed.";
    case "state":
      return "That sign-in took too long or was started in another tab. Try again.";
    case "twitch":
      return "Twitch could not confirm who you are. Try again in a moment.";
    case "disabled":
      return "Sign in with Twitch is not switched on for this console.";
    default:
      return null;
  }
}
