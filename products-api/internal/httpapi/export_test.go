package httpapi

type (
	Problem = problem
	Code    = code
)

const (
	CodeValidation         = codeValidation
	CodeProductNotFound    = codeProductNotFound
	CodeResourceNotFound   = codeResourceNotFound
	CodeMethodNotAllowed   = codeMethodNotAllowed
	CodePreconditionFailed = codePreconditionFailed
	CodeUnauthorized       = codeUnauthorized
	CodeForbidden          = codeForbidden
	CodeRateLimited        = codeRateLimited
	CodeClientClosed       = codeClientClosed
	CodeInternal           = codeInternal
	CodeBadGateway         = codeBadGateway
	CodeServiceUnavailable = codeServiceUnavailable
	RouteProduct           = "GET " + pathProduct
	StatusClientClosed     = statusClientClosedRequest
)
