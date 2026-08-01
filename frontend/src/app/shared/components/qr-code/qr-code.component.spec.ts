import { ComponentFixture, TestBed } from '@angular/core/testing';
import QRCode from 'qrcode';
import { QrCodeComponent } from './qr-code.component';

describe('QrCodeComponent', () => {
  let fixture: ComponentFixture<QrCodeComponent>;

  beforeEach(async () => {
    vi.spyOn(QRCode, 'toCanvas').mockImplementation(() => Promise.resolve({} as HTMLCanvasElement));

    await TestBed.configureTestingModule({
      imports: [QrCodeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(QrCodeComponent);
    fixture.componentRef.setInput('value', 'TICKETWAVE:ABC234');
    fixture.detectChanges();
  });

  it('renders a canvas labelled with the encoded value', () => {
    const canvas = (fixture.nativeElement as HTMLElement).querySelector('canvas');
    expect(canvas).not.toBeNull();
    expect(canvas?.getAttribute('aria-label')).toBe('QR code for TICKETWAVE:ABC234');
  });

  it('encodes the value via the qrcode library', () => {
    expect(QRCode.toCanvas).toHaveBeenCalledWith(
      expect.any(HTMLCanvasElement),
      'TICKETWAVE:ABC234',
      expect.objectContaining({ width: 160 }),
    );
  });
});
