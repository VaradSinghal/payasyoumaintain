# Notification & Advisory Service

**Tech Stack:** Java 21 / Spring Boot

## Responsibility

This service handles customer notifications and proactive maintenance advisories. It handles:

- **Push Notifications** — sending real-time alerts to the mobile app (FCM / APNs).
- **Email & SMS** — transactional and marketing communications via templated channels.
- **Maintenance Advisories** — proactively alerting customers about upcoming or overdue maintenance based on OEM schedules and vehicle health signals.
- **Premium Impact Alerts** — notifying customers when their behaviour (driving, maintenance) is positively or negatively affecting their premium.
- **Notification Preferences** — respecting per-customer channel preferences and quiet hours.

## API Contracts

_To be defined._

## Dependencies

- Apache Kafka (event consumption)
- PostgreSQL (notification log)
- FCM / APNs (push delivery)
- SendGrid / Twilio (email & SMS)
