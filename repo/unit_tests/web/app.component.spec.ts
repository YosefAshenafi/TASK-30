import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from '../../web/src/app/app.component';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AppComponent);
  });

  it('renders the router-outlet so child routes can be projected', () => {
    fixture.detectChanges();
    const outlet = fixture.nativeElement.querySelector('router-outlet');
    expect(outlet).withContext('router-outlet host is required for lazy routes').not.toBeNull();
  });

  it('exposes a component instance so the host bootstrap can attach it', () => {
    const instance = fixture.componentInstance;
    expect(instance).withContext('AppComponent must be constructable').toBeInstanceOf(AppComponent);
  });

  it('renders without throwing when the component tree is initialised', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
  });
});
