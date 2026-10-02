# Contract Worksheet

One section per milestone. Fill each one in as you go, in order. Write each
prediction before you run anything. That is the part a TA asks about.

Keep it short and specific. Point at methods, call sites, and error text.

---

## Milestone 1: The notes overload

### Prediction (write this before you run the build, and you can deliberate with your agent)

**Will the consumer, untouched, still compile and pass?** Yes.

**Why.** The calls in `FrontDesk.java:27` and `FrontDesk.java:33` still pass
four arguments, so the compiler selects the existing four-parameter method.
The new overload takes five arguments and creates no ambiguity. The old method
keeps the same booking behavior and defaults notes to null, so the consumer's
tests should still pass.

### What happened

**The result.** `mvn -B test` passed. A fresh `mvn -B clean test` also
recompiled both modules and passed, with `consumer/` unchanged:

```text
api:
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
consumer:
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

[INFO] lab06-booking-parent ............................... SUCCESS [  0.794 s]
[INFO] lab06-api .......................................... SUCCESS [  0.839 s]
[INFO] lab06-consumer ..................................... SUCCESS [  0.363 s]
[INFO] BUILD SUCCESS
```

The first clean attempt was blocked by local Maven cache permissions before
compilation; rerunning with the required access produced the result above.



**Is an additive change always safe in Java?** No. Adding `f(Integer)` alongside
`f(String)` makes an existing `f(null)` call ambiguous, so it no longer compiles.
Our new overload has a different argument count, so this ambiguity does not occur.

---

## Milestone 2: The request object

### Prediction (write this before you run the build)

**Will the untouched consumer still compile and pass?** No. The consumer module
will fail during compilation because the four-parameter method is removed.

**Where.** `FrontDesk.java:27` and `FrontDesk.java:33` still call
`createBooking(roomId, startMinute, endMinute, key)` instead of passing a
`BookingRequest`.

**What about the tests in `api/`, after you update them?** All five should pass
after migration to the request method because booking behavior stays the same.
This does not prove compatibility: those tests use the new API, while the
consumer still uses the removed method.

### Step 1: after the fold

**What the build printed.** `mvn -B clean test`:

```text
api:
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
consumer compilation:
[ERROR] /Users/juewei/Study/17514/f26-lab06/consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:[27,19] method createBooking in interface edu.cmu.cs214.booking.BookingApi cannot be applied to given types;
  required: edu.cmu.cs214.booking.BookingRequest
  found:    java.lang.String,long,long,<nulltype>
  reason: actual and formal argument lists differ in length
[ERROR] /Users/juewei/Study/17514/f26-lab06/consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:[33,19] method createBooking in interface edu.cmu.cs214.booking.BookingApi cannot be applied to given types;
  required: edu.cmu.cs214.booking.BookingRequest
  found:    java.lang.String,long,long,java.lang.String
  reason: actual and formal argument lists differ in length

[INFO] lab06-booking-parent                                               [pom]
[INFO] lab06-api                                                          [jar]
[INFO] lab06-consumer                                                     [jar]
[INFO] lab06-booking-parent ............................... SUCCESS [  0.072 s]
[INFO] lab06-api .......................................... SUCCESS [  0.870 s]
[INFO] lab06-consumer ..................................... FAILURE [  0.046 s]
[INFO] BUILD FAILURE
```

**Which module's tests ran, and which did not.** All five API tests ran and
passed. Consumer compilation failed, so none of its seven tests ran. The
consumer compiler detected the broken contract; the migrated API tests did not.

### Step 2: the deprecation path

**What you added.** Restored both overloads as `@Deprecated` default methods
in `BookingApi`:

```java
Booking createBooking(String roomId, long startMinute, long endMinute,
                      String waitlistKey);
Booking createBooking(String roomId, long startMinute, long endMinute,
                      String waitlistKey, String notes);
```

Both construct a `BookingRequest` and delegate to `createBooking(BookingRequest)`.
The four-parameter overload supplies null notes. Their `@deprecated` Javadoc
names the replacement.

**The warnings.** From the second `mvn -B clean test`:

```text
[WARNING] /Users/juewei/Study/17514/f26-lab06/consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:[27,19] createBooking(java.lang.String,long,long,java.lang.String) in edu.cmu.cs214.booking.BookingApi has been deprecated
```

The same warning appeared at `FrontDesk.java:[33,19]`. Both modules now pass:

```text
api:
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
consumer:
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

[INFO] lab06-booking-parent                                               [pom]
[INFO] lab06-api                                                          [jar]
[INFO] lab06-consumer                                                     [jar]
[INFO] lab06-booking-parent ............................... SUCCESS [  0.072 s]
[INFO] lab06-api .......................................... SUCCESS [  0.848 s]
[INFO] lab06-consumer ..................................... SUCCESS [  0.369 s]
[INFO] BUILD SUCCESS
```

**What the deprecation path resolves.** The unchanged consumer can build again.
The API team can offer the request method now, while the consumer team migrates
on its own schedule before any future removal of the old methods.

**What the warnings accomplish that a README note would not.** During compilation,
the consumer team sees warnings naming the old method and the exact file and
line to update. They do not have to look for a migration note in the README.

---

## Milestone 3: The misuse critique

Not coded. One misuse, one redesign, one cost. Discuss it with your TA.

### The misuse

**What is easy to get wrong.** The boolean in `cancelBooking` does not explain
whether cancellation should promote a waitlisted booking. A caller can reverse
true and false, and the compiler still accepts it.

**The call site.** `consumer/src/main/java/edu/cmu/cs214/frontdesk/FrontDesk.java:48`:

```java
return api.cancelBooking(bookingId, true);
```

The literal `true` alone does not reveal its meaning. Line 53 uses `false`
for quiet cancellation.

**What goes wrong when it happens.** Passing false when promotion is intended
silently leaves eligible guests WAITLISTED. Passing true for a quiet correction
can unexpectedly promote a guest. Cancellation can return true in either case,
so its return value does not reveal the mistake.

### The redesign

**The proposal.** Replace the boolean with an explicit policy:

```java
enum CancellationPolicy {
    PROMOTE_FIRST_ELIGIBLE,
    KEEP_WAITLIST_UNCHANGED
}

boolean cancelBooking(long bookingId, CancellationPolicy policy);

// Replacement for the call at FrontDesk.java:48:
return api.cancelBooking(bookingId, CancellationPolicy.PROMOTE_FIRST_ELIGIBLE);
```

**Why the mistake is now hard or impossible to make.** The compiler rejects a
bare boolean at the new signature, and the enum name makes the intended behavior
visible at the call site. Choosing the wrong enum value is still possible, but
easier to notice in review. The implementation should reject null policies.

### One tradeoff

**What it costs.** Callers must learn an extra type and migrate their calls.
Replacing the boolean method immediately would break existing consumers again.
A migration would keep it as a deprecated adapter: true maps to
PROMOTE_FIRST_ELIGIBLE and false to KEEP_WAITLIST_UNCHANGED. That temporarily
leaves two methods to document and maintain.

**When the price is worth paying.** When multiple teams use the API or an
accidental promotion affects real room allocations, explicit intent is worth
the extra type and migration work.
