# Geogram messages

There are different types of messages according to context.
Most of them are written for everyone to read, while others
might be private and meant for specific users.

On this page are detailed the types of messages.

# Generic characteristics

+ type (see sections below)
+ id (generated automatically)
+ timestamp (generated automatically)
+ author id
+ title
+ content (markdown syntax)
+ list of related messages
+ status (ACTIVE/INACTIVE)
+ expiry date (optional)
+ button actions (optional)


## Message delegation

Given the decentralized nature of geogram devices, it is
expected that each device has an administrator/owner that
controls both the hardware and the settings for the
geogram beacon.

The owner of the beacon does not need to perform tasks
on his own, he can delegate these tasks to other users.


## WELCOME message

An introductory message to the user when arriving in reach of
the beacon. The message contains a short description of the
location itself and links to other messages. For example to
the details or upcoming events and relevant notifications.

Characteristics:
+ mandatory
+ once
+ short text

Color:
+ customizable
+ can include CSS theme


## DETAILS message

A blog-style message that describes the notable facts of the
location itself, for example, an historical description of
past event so that tourists can be informed.

Characteristics:
+ optional
+ on demand
+ long text with links

Color:
+ blue

## STATS message

Message that is updated with live stats from the beacon.
For example, the number of visitors on that day and overall across
the year. The local temperature, a picture from a live camera or
other statistics that are considered relevant to present the
audience.

Characteristics:
+ optional
+ on demand
+ long text with links

Color:
+ blue


## EMERGENCY message

This is the most critical type of message, it is used for
cases of natural disasters (e.g. earthquakes, storms, floods)
but can also be used for man-related disaster such as crimes,
terrorist attacks, infrastructure failure or accidents.

The goal is that people need to be warned as soon as possible
and with as much alert as possible.

Given the criticality of this type of message, it should only
be delegated to people such as firefighters, town-hall mayors,
police department and similar.

Characteristics:
+ mandatory
+ once
+ time based
+ sound alarm
+ short text

Color:
+ red


## EVENT message

Specifies an event happening on the location.
The message includes a date for start and end of the
event, includes other fields such as entry cost or
specific requirements such as dress-code.

This kind of messages are placed on the calendar
and have (optional) details if the user is planning
to attend (or not). Events can specify

Characteristics:
+ optional
+ on demand
+ long text with links

Color:
+ green


## CHAT message

Some beacons permit to leave public chat messages.
The goal is let people arriving to the beacon to leave
short information to other users.

Each message includes the public identifier of the user
so that private messages can be sent if needed.

By default only the last 100 messages are displayed to
the users. The administrator is responsible to monitor
the chat conversations and make sure it follows the
necessary behavior guidelines.

Users can be temporarily suspended or banned from using
the chat system by the administrator when necessary.


## PRIVATE message

Some beacons permit users to write private messages for
other users. These messages will be stored by a period
of time defined by the administrator and follow a specific
set of rules (e.g. size of messages, attachments allowed).

When the target user reaches the beacon and is identified,
the beacon will send the specific for that user. Before
delivering the message, the beacon needs to be verify the
authenticity of the user. This is usually done by requesting
a signed reply from the target user from his private key.

Messages can opt to use encryption or to leave them in plain
text.
