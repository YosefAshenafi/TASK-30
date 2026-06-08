import { TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { CourseManagementComponent } from './course-management.component';
import { ApiService } from '../core/api.service';

describe('CourseManagementComponent', () => {
  let apiService: jasmine.SpyObj<ApiService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const courses = [
    { id: 'c1', title: 'Safety 101', version: '1.0', location: 'Lab', instructor: 'faculty1' },
  ];

  beforeEach(() => {
    apiService = jasmine.createSpyObj('ApiService', ['get', 'post', 'put']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    // GET /courses returns a Spring Page; the component must unwrap `content`.
    apiService.get.and.callFake((url: string): any => {
      if (url === '/courses') return of({ content: courses });
      return of({ content: [] });
    });

    TestBed.configureTestingModule({
      imports: [CourseManagementComponent],
      providers: [
        { provide: ApiService, useValue: apiService },
        { provide: MatSnackBar, useValue: snackBar },
        provideNoopAnimations(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });
    TestBed.overrideProvider(MatSnackBar, { useValue: snackBar });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(CourseManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads courses on init, unwrapping the Page content', fakeAsync(() => {
    const fixture = TestBed.createComponent(CourseManagementComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.courses).toEqual(courses);
    expect(fixture.componentInstance.loading).toBeFalse();
  }));

  it('save() POSTs a new course when not editing', fakeAsync(() => {
    apiService.post.and.returnValue(of({ id: 'c2' }));
    const fixture = TestBed.createComponent(CourseManagementComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.courseForm.patchValue({ title: 'New Course', location: 'Room 2', instructor: 'corp1' });
    comp.save();
    flush();

    expect(apiService.post).toHaveBeenCalledWith('/courses', jasmine.objectContaining({ title: 'New Course' }));
    expect(comp.saving).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith('Course created.', 'Dismiss', jasmine.any(Object));
  }));

  it('editCourse() then save() PUTs to the selected course id', fakeAsync(() => {
    apiService.put.and.returnValue(of({ id: 'c1' }));
    const fixture = TestBed.createComponent(CourseManagementComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.editCourse(courses[0]);
    expect(comp.editingId).toBe('c1');
    expect(comp.courseForm.get('title')!.value).toBe('Safety 101');

    comp.save();
    flush();

    expect(apiService.put).toHaveBeenCalledWith('/courses/c1', jasmine.objectContaining({ title: 'Safety 101' }));
    expect(snackBar.open).toHaveBeenCalledWith('Course updated.', 'Dismiss', jasmine.any(Object));
    expect(comp.editingId).toBeNull();
  }));

  it('sets errorMessage when course load fails', fakeAsync(() => {
    apiService.get.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(CourseManagementComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load courses.');
  }));
});
