# An Introduction to tCketManage

## Prelude

Thank you for choosing tCketManage as your ticketing solution! tCketManage is the first fully open source, fully featured customizable ticketing platform designed to meet the needs of event organizers of all sizes. With tCketManage, you can create and manage events, sell tickets online, and handle ticket scanning and validation with ease.

This user manual is designed to help you get the most out of tCketManage through its features, configuration options, and API usage. Whether you're organizing a small local event or a large multi-day convention, tCketManage provides the tools you need to manage your ticketing operations efficiently and effectively. From setting up your first event to handling complex ticket sales and scanning scenarios, this guide will walk you through everything you need to know to make your event a success with tCketManage. Let's get started!

## Prerequisites

This guide assumes you have familiarity with the basics of Java development and Spring Boot applications. For the best experience, it's recommended to have Java 21 installed, along with Docker for containerized deployment (optional but recommended).

## Important Terminology

tCketManage uses specific terminology to describe its core concepts. Understanding these terms is crucial for effectively using the platform:

- **Event**: The base entity representing a ticketed event. An event can have multiple _zones_ and _ticket types_
= **Zone**: A named area within an event that can have specific access restrictions and ticket entitlements. Think of zones as different sections of a venue, such as VIP areas, general admission, or specific seating sections. You can also use them for more abstract purposes such as Food & Beverage access, or Online vs In-Person access.
- **Ticket Type**: A specific category of ticket for an event, which can have different prices, zone access + reentries, and availability windows. For example, you might have "Early Bird" ticket type with limited availability and a lower price, and a "General Admission" ticket type with standard pricing. You may also have a "VIP" ticket type that grants access to exclusive _zones_ within the event.
- **Order**: A collection of tickets purchased by a customer. An order can contain multiple tickets of different types and is associated with a specific event. Each order has a unique identifier and can be tracked for payment status, fulfillment, and scanning history.
- **Ticket**: A unique, cryptographically signed token that represents a customer's entry to an event. Each ticket is associated with **one** specific event and **one** specific ticket type. Tickets are generated upon successful order completion and can be delivered via email or other means. Each ticket contains a QR code that can be scanned at the event for entry validation.
- **Scan Event**: A record of a ticket being scanned at an event. Scan events are logged with a timestamp, the zone where the scan occurred, and the result of the scan (e.g., valid, invalid, already used). This allows event organizers to track attendance and manage access control in real-time.
