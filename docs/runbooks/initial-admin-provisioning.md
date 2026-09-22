# Initial Administrator Provisioning

## Purpose

This runbook defines the approval and handoff process for establishing the first administrator in a new Campus Service environment. It does not provide credentials, SQL, token material, or a runtime provisioning mechanism.

## Preconditions

- The environment owner has approved the named administrator and verified their organizational identity through an approved out-of-band process.
- The deployment has completed its approved database migration and application verification process.
- A separate, authorized operational procedure or approved break-glass process is available to create the first active user with the `ADMIN` role. This repository does not implement that procedure.

## Procedure

1. Record the approver, intended administrator identity, environment, and time in the organization's approved change record.
2. Use the approved operational procedure to establish exactly one active administrator. Do not place passwords, hashes, refresh values, JWTs, or connection details in the change record.
3. Have the administrator authenticate through the normal application flow and confirm access only to the permitted administrator capabilities.
4. Create any additional administrators through the authenticated administrator user-management API, following least-privilege role assignment and the approved audit process.
5. Close the change record with verification evidence that excludes credentials and other secret material.

## Safety And Recovery

- Do not share, log, commit, or document passwords, token values, signing keys, database credentials, or connection strings.
- Do not bypass the final-active-administrator safeguard when changing administrator status or roles.
- If the initial administrator cannot authenticate or access is suspected to be compromised, stop and use the organization's approved incident or break-glass procedure. Do not improvise database changes from this runbook.
