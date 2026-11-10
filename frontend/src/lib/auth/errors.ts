/** An error returned by Cognito (API `__type` or OAuth `error` code). */
export class CognitoError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "CognitoError";
  }
}

/**
 * User-facing messages for Cognito errors. Credential failures deliberately share one message so
 * the form never reveals whether an account exists.
 */
export function describeCognitoError(error: unknown): string {
  if (!(error instanceof CognitoError)) {
    return "Something went wrong. Please try again.";
  }
  switch (error.code) {
    case "NotAuthorizedException":
    case "UserNotFoundException":
      return "Incorrect username or password.";
    case "UsernameExistsException":
      return "That username is already taken.";
    case "InvalidPasswordException":
      return "That password doesn't meet the requirements.";
    case "CodeMismatchException":
      return "That code is incorrect.";
    case "ExpiredCodeException":
      return "That code has expired. Request a new one.";
    case "LimitExceededException":
    case "TooManyRequestsException":
    case "TooManyFailedAttemptsException":
      return "Too many attempts. Please wait a few minutes and try again.";
    case "AliasExistsException":
      return "An account with that email already exists.";
    case "CodeDeliveryFailureException":
      return "We couldn't send the code. Check the email address and try again.";
    default:
      return "Something went wrong. Please try again.";
  }
}
