import { Component, ElementRef, afterRenderEffect, input, viewChild } from '@angular/core';
import QRCode from 'qrcode';

/** Renders `value` as a scannable QR code onto a canvas. Used for the e-ticket's check-in code and the demo Pix key. */
@Component({
  selector: 'tw-qr-code',
  templateUrl: './qr-code.component.html',
  styleUrl: './qr-code.component.scss',
})
export class QrCodeComponent {
  readonly value = input.required<string>();
  readonly size = input(160);

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  constructor() {
    afterRenderEffect(() => {
      QRCode.toCanvas(this.canvas().nativeElement, this.value(), {
        width: this.size(),
        margin: 1,
        color: { dark: '#1a1a2e', light: '#ffffff' },
      }).catch(() => {
        // Malformed input for the QR encoder is not something a customer can
        // act on - the canvas simply stays blank rather than surfacing an error.
      });
    });
  }
}
