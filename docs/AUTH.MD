# tCketManage - Authentication and Authorization

By default, tCketManage does not contain any authentication subsystem. Rather, it assumes you have your own identity provider and that you will integrate with it by enabling Spring Security and implementing a `UserDetailsService` that loads users from your database or another source.

With this assumption, we use `PreAuthorize` annotations on all operator endpoints (e.g. event management, scanning) to enforce role-based access control. The standalone `tcketmanage-app` module does not enable method security, so all endpoints are open for development and testing purposes (for now). However, when you embed tCketManage into a host application, you should enable Spring method security and ensure that your `UserDetailsService` correctly populates user roles (e.g. `SCANNER`, `EVENT_MANAGER`, `ADMIN`) to enforce the intended access controls.
