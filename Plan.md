# tCketManage - Plan For the Future

The ultimate plan for tCketManage is to turn it into a "starter" package similar to Spring Boot Starter packages, with annotations to bridge the internal models to external ones (i.e: whatever your codebase uses). You can add it to your existing project and plug into its many functionalities, and integrate them directly into your existing business workflow.

Stuff like Auth, event models, and branding can be handled by the user of the package through annotations, and the package itself can be focused on core ticketing logic and concurrency management. Of course, it will still be able to run as a standalone app with the default models and a simple auth system for testing and small events.

## Authentication and Authorization

tCketManage will support role-based access control via Spring Securities GrantedAuthority System. The package will define a few core roles (e.g. ADMIN, EVENT_MANAGER, SCANNER) which can be used with `@PreAuthorize` annotations on service methods. Users of the package can then map these roles to their own auth system (e.g. JWT claims, database roles) through a simple adapter interface.

Authentication is something under consideration - OAuth with JWT are the most likely candidates, however this will also need to be pluggable into existing auth workflows and providers (i.e: using tCketManage as a library in an existing codebase with its own auth system).

## Payment Providers

Stripe implementation is on the roadmap, however Interac E-Transfer is the main focus for now as it's the most commonly requested payment method for the target user base (small event organizers in Canada). The E-Transfer flow is already implemented and functional, but it needs more work to be robust and operator-friendly (e.g. better email parsing, a quarantine system for suspicious emails, and an admin UI for manual confirmation).

## Ticket Theming and Designs

Currently we use Thymeleaf for ticket fields within a simple SVG template. The ultimate goal is to expand the template into a theming system where users can define their own colors and branding elements managed through thymeleaf stylesheets in the SVG template. This will allow event organizers to create tickets that match their event's branding without needing to modify the core codebase. Additionally, we can provide a few pre-built ticket designs that users can choose from or use as a starting point for their own custom designs.

The ultimate ultimate goal here is to plug into HuggingFace models and turn a text description of a desired ticket design into an actual SVG template, but that's a bit further down the road and will likely require some experimentation to get right.

## Mobile Wallet Integration (Apple Wallet, Google Pay)

A far fetch for now, however something on the roadmap. The idea is to generate the appropriate pass files for Apple Wallet and Google Pay, allowing attendees to add their tickets to their mobile wallets for easy access at the event, without a specific app required.re

Apple Wallet is likely a farfetch as it requires a $130/year developer account and a Mac to generate the necessary certificates. Google Wallet should (in theory) be much more straight forward.
