package pt.ulisboa.tecnico.socialsoftware.tutor.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.AnswerService;
import pt.ulisboa.tecnico.socialsoftware.tutor.dashboard.services.DifficultQuestionService;
import pt.ulisboa.tecnico.socialsoftware.tutor.demo.DemoUtils;
import pt.ulisboa.tecnico.socialsoftware.tutor.execution.domain.CourseExecution;
import pt.ulisboa.tecnico.socialsoftware.tutor.execution.repository.CourseExecutionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.impexp.ImpExpService;

import java.util.List;

// On by default. --scheduled-tasks.enabled=false turns the tasks off, for example for atg: the
// reset of the demo course at 01:00 deletes the data that the tests use, and the other tasks
// change the statistics or export every course while the tests run.
@Component
@ConditionalOnProperty(name = "scheduled-tasks.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTasks {
    @Autowired
    private ImpExpService impExpService;

    @Autowired
    private DemoUtils demoUtils;

    @Autowired
    private AnswerService answerService;

    @Autowired
    private CourseExecutionRepository courseExecutionRepository;

    @Autowired
    private DifficultQuestionService difficultQuestionService;

    @Scheduled(cron = "0 0 3,13 * * *")
    public void exportAll() {
        impExpService.exportAll();
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void resetDemoInfo() {
        demoUtils.resetDemoInfo();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void writeQuizAnswersAndCalculateStatistics() {
        answerService.writeQuizAnswersAndQuestionStatistics();
    }

    @Scheduled(cron = "0 0 5 * * *")
    public void updateCourseExecutionsDifficultQuestions() {
        List<CourseExecution> courseExecutions = courseExecutionRepository.findAll();

        courseExecutions.forEach(courseExecution -> difficultQuestionService.updateCourseExecutionWeekDifficultQuestions(courseExecution.getId()));
    }


}