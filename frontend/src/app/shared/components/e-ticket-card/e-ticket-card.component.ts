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
}
