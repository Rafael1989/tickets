import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { BookingDetailResponse } from '../../../core/models/booking.model';
import { SeatResponse, ScheduleSearchResult } from '../../../core/models/catalog.model';
import { PassengerResponse } from '../../../core/models/passenger.model';
import { NotificationService } from '../../../core/services/notification.service';
import { QrCodeComponent } from '../qr-code/qr-code.component';

/**
 * Boarding-pass-style e-ticket: PNR, route/schedule, seat assignments, a
 * check-in QR code, and ticket actions. Used both right after a successful
 * checkout payment and on the booking-details page, so the same card always
 * renders the same way regardless of how the customer got there.
 */
@Component({
  selector: 'tw-e-ticket-card',
  imports: [DatePipe, QrCodeComponent],
  templateUrl: './e-ticket-card.component.html',
  styleUrl: './e-ticket-card.component.scss',
})
export class ETicketCardComponent {
  private readonly notifications = inject(NotificationService);

  readonly booking = input.required<BookingDetailResponse>();
  readonly schedule = input<ScheduleSearchResult | null>(null);
  readonly seats = input<SeatResponse[]>([]);
  readonly passengers = input<PassengerResponse[]>([]);

  readonly checkInCode = computed(() => `TICKETWAVE:${this.booking().booking.pnr}`);

  seatLabel(seatId: number): string {
    const seat = this.seats().find((s) => s.id === seatId);
    return seat ? `${seat.seatNumber} · ${seat.seatClass}` : `#${seatId}`;
  }

  passengerName(passengerId: number): string {
    return this.passengers().find((p) => p.id === passengerId)?.fullName ?? `#${passengerId}`;
  }

  async downloadPdf(): Promise<void> {
    const { default: QRCode } = await import('qrcode');
    const { jsPDF } = await import('jspdf');

    const detail = this.booking();
    const schedule = this.schedule();
    const doc = new jsPDF({ format: 'a6', unit: 'mm' });

    doc.setFontSize(16);
    doc.text('TicketWave e-ticket', 8, 12);

    doc.setFontSize(11);
    doc.text(`PNR: ${detail.booking.pnr}`, 8, 22);
    if (schedule) {
      const route = schedule.destination ? `${schedule.origin} -> ${schedule.destination}` : (schedule.venue ?? '');
      doc.text(route, 8, 29);
      doc.setFontSize(9);
      doc.text(`Departs: ${new Date(schedule.departureTime).toLocaleString()}`, 8, 35);
    }

    doc.setFontSize(9);
    let y = 43;
    for (const item of detail.items) {
      doc.text(`${this.seatLabel(item.seatId)} — ${this.passengerName(item.passengerId)}`, 8, y);
      y += 5;
    }

    const qrDataUrl = await QRCode.toDataURL(this.checkInCode(), { margin: 1 });
    doc.addImage(qrDataUrl, 'PNG', 70, 20, 30, 30);

    doc.save(`ticketwave-${detail.booking.pnr}.pdf`);
  }

  emailTicket(): void {
    // No email/notification backend exists in this stack yet — surfacing
    // that honestly rather than faking a "sent" confirmation for an email
    // that never actually goes anywhere.
    this.notifications.info("Email delivery isn't wired up in this demo yet — use Download PDF for now.");
  }

  /**
   * Builds a structurally correct but UNSIGNED .pkpass (a zip of pass.json +
   * manifest.json with a SHA-1 digest of pass.json, per Apple's PassKit
   * format). Real Apple/Google Wallet passes require a detached signature
   * produced with a Pass Type ID certificate issued by the Apple Developer
   * Program — we don't have one, so this file has no `signature` entry and
   * will be rejected by any real wallet app. It exists purely to demonstrate
   * the pass structure; the UI is explicit about that limitation.
   */
  async downloadWalletPass(): Promise<void> {
    const { default: JSZip } = await import('jszip');

    const detail = this.booking();
    const schedule = this.schedule();
    const pnr = detail.booking.pnr;

    const primaryFields = schedule
      ? [
          { key: 'origin', label: schedule.venue ? 'Venue' : 'From', value: schedule.origin ?? schedule.venue ?? '' },
          ...(schedule.destination ? [{ key: 'destination', label: 'To', value: schedule.destination }] : []),
        ]
      : [];

    const passJson = {
      formatVersion: 1,
      passTypeIdentifier: 'pass.com.ticketwave.demo',
      teamIdentifier: 'DEMOTEAMID',
      serialNumber: pnr,
      organizationName: 'TicketWave',
      description: `TicketWave e-ticket ${pnr}`,
      logoText: 'TicketWave',
      foregroundColor: 'rgb(255, 255, 255)',
      backgroundColor: 'rgb(36, 113, 163)',
      barcodes: [{ format: 'PKBarcodeFormatQR', message: this.checkInCode(), messageEncoding: 'iso-8859-1' }],
      boardingPass: {
        transitType: 'PKTransitTypeGeneric',
        primaryFields,
        secondaryFields: detail.items.map((item, index) => ({
          key: `seat-${index}`,
          label: 'Seat',
          value: this.seatLabel(item.seatId),
        })),
        auxiliaryFields: schedule
          ? [{ key: 'departs', label: 'Departs', value: new Date(schedule.departureTime).toLocaleString() }]
          : [],
        backFields: [{ key: 'pnr', label: 'PNR', value: pnr }],
      },
    };

    const passBytes = new TextEncoder().encode(JSON.stringify(passJson));
    const manifest = { 'pass.json': await sha1Hex(passBytes) };

    const zip = new JSZip();
    zip.file('pass.json', passBytes);
    zip.file('manifest.json', JSON.stringify(manifest));
    const blob = await zip.generateAsync({ type: 'blob' });

    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ticketwave-${pnr}.pkpass`;
    link.click();
    URL.revokeObjectURL(url);

    this.notifications.info(
      "This is an unsigned demo pass — it won't import into Apple/Google Wallet, which require a real signing certificate this demo doesn't have.",
    );
  }
}

async function sha1Hex(data: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-1', data as unknown as ArrayBuffer);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}
