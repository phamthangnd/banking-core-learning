# API Guidelines

Base path: `/api/v1`

Naming:
- nouns for resources
- explicit action endpoints only when they represent commands
- pagination for potentially large collections
- filtering through query parameters or dedicated search requests

Every API must document:
- authentication
- authorization
- request
- response
- validation errors
- business errors
- idempotency requirements where applicable
