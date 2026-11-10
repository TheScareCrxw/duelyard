/** RFC 9457 problem response returned by the Spring Boot API on errors. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}
