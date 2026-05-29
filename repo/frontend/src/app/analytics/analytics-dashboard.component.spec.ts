import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AnalyticsDashboardComponent } from './analytics-dashboard.component';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

describe('AnalyticsDashboardComponent', () => {
  let apiService: jasmine.SpyObj<ApiService>;
  let authService: jasmine.SpyObj<AuthService>;

  // Fixtures mirror the component's model interfaces (MasteryTrend, WrongAnswer, KnowledgeGap,
  // ItemDifficulty) so they are assignable where the typed component arrays are asserted.
  const masteryData = [
    { week: '2025-W01', masteryRate: 75, totalAttempts: 40 },
  ];
  const wrongAnswerData = [
    { itemId: 'i1', question: 'Question 1', knowledgePoint: 'Algebra', wrongCount: 12, wrongRate: 0.3 },
  ];
  const knowledgeGapData = [
    { knowledgePoint: 'Algebra', wrongRate: 0.42, totalAttempts: 8 },
  ];
  const itemDiffData = [
    { itemId: 'i2', question: 'Hard Question', storedDifficulty: 0.3, observedDifficulty: 0.35, discrimination: 0.55, totalAttempts: 20 },
  ];

  beforeEach(() => {
    apiService = jasmine.createSpyObj('ApiService', ['get']);
    authService = jasmine.createSpyObj('AuthService', ['hasRole', 'getUserId']);
    authService.hasRole.and.returnValue(false);

    TestBed.configureTestingModule({
      imports: [AnalyticsDashboardComponent, ReactiveFormsModule],
      providers: [
        { provide: ApiService, useValue: apiService },
        { provide: AuthService, useValue: authService },
        provideNoopAnimations(),
        provideRouter([]),
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });
  });

  it('should be created', () => {
    apiService.get.and.returnValue(of(masteryData));
    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads mastery trends on init (tab 0)', fakeAsync(() => {
    apiService.get.and.returnValue(of(masteryData));
    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
    tick();

    expect(apiService.get).toHaveBeenCalledWith(
      jasmine.stringMatching('/analytics/mastery'),
      jasmine.any(Object)
    );
    expect(fixture.componentInstance.masteryTrends).toEqual(masteryData);
    expect(fixture.componentInstance.loadingMastery).toBeFalse();
  }));

  it('sets errorMessage when mastery load fails', fakeAsync(() => {
    apiService.get.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load mastery trends.');
    expect(fixture.componentInstance.loadingMastery).toBeFalse();
  }));

  it('loads wrong answers on tab change to index 1', fakeAsync(() => {
    apiService.get.and.returnValue(of(wrongAnswerData));
    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onTabChange({ index: 1 });
    tick();

    expect(apiService.get).toHaveBeenCalledWith(
      jasmine.stringMatching('/analytics/wrong-answers'),
      jasmine.any(Object)
    );
    expect(fixture.componentInstance.wrongAnswers).toEqual(wrongAnswerData);
  }));

  it('loads knowledge gaps on tab change to index 2', fakeAsync(() => {
    apiService.get.and.callFake((url: string): any => {
      if (url.includes('knowledge-gaps')) return of(knowledgeGapData);
      return of([]);
    });
    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onTabChange({ index: 2 });
    tick();

    expect(fixture.componentInstance.knowledgeGaps).toEqual(knowledgeGapData);
  }));

  it('loads item difficulty on tab change to index 3', fakeAsync(() => {
    apiService.get.and.callFake((url: string): any => {
      if (url.includes('item-difficulty')) return of(itemDiffData);
      return of([]);
    });
    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onTabChange({ index: 3 });
    tick();

    expect(fixture.componentInstance.itemDifficulty).toEqual(itemDiffData);
  }));

  it('applyFilters() reloads current active tab', fakeAsync(() => {
    apiService.get.and.returnValue(of(masteryData));
    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
    tick();

    const callsBefore = (apiService.get as jasmine.Spy).calls.count();
    fixture.componentInstance.applyFilters();
    tick();

    expect((apiService.get as jasmine.Spy).calls.count()).toBeGreaterThan(callsBefore);
  }));

  it('adds orgId param when user is CORPORATE_MENTOR', fakeAsync(() => {
    authService.hasRole.and.callFake((role: string) => role === 'CORPORATE_MENTOR');
    authService.getUserId.and.returnValue('org-123');
    apiService.get.and.returnValue(of(masteryData));

    const fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
    tick();

    const callArgs = (apiService.get as jasmine.Spy).calls.mostRecent().args;
    expect(callArgs[1]).toEqual(jasmine.objectContaining({ orgId: 'org-123' }));
  }));
});
